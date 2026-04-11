package com.mappo.controlplane;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mappo.controlplane.application.release.ReleaseManifestSourceClient;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ReleaseManifestIngestIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void ingestGithubManifestCreatesManagedAppReleases() throws Exception {
        mockMvc.perform(post("/api/v1/admin/releases/ingest/github")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "projectId": "azure-managed-app-deployment-stack"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifestReleaseCount").value(4))
            .andExpect(jsonPath("$.createdCount").value(2))
            .andExpect(jsonPath("$.skippedCount").value(1))
            .andExpect(jsonPath("$.ignoredCount").value(1))
            .andExpect(jsonPath("$.createdReleaseIds.length()").value(2));

        mockMvc.perform(post("/api/v1/admin/releases/ingest/github")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "projectId": "azure-managed-app-deployment-stack"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifestReleaseCount").value(4))
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.skippedCount").value(3))
            .andExpect(jsonPath("$.ignoredCount").value(1));

        mockMvc.perform(get("/api/v1/releases"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].sourceRef").value("github://cvonderheid/mappo-managed-app/managed-app/mainTemplate.json"))
            .andExpect(jsonPath("$[*].sourceVersion", containsInAnyOrder("2026.03.06.1", "2026.03.07.1")));
    }

    @TestConfiguration
    static class ReleaseManifestClientStubConfig {

        @Bean
        @Primary
        ReleaseManifestSourceClient releaseManifestSourceClient() {
            return new ReleaseManifestSourceClient() {

                @Override
                public ReleaseIngestProviderType provider() {
                    return ReleaseIngestProviderType.github;
                }

                @Override
                public String fetchManifest(String repo, String path, String ref) {
                    return """
                {
                  "releases": [
                    {
                      "publication_status": "published",
                      "source_ref": "github://cvonderheid/mappo-managed-app/managed-app/mainTemplate.json",
                      "source_version": "2026.03.06.1",
                      "source_type": "deployment_stack",
                      "source_version_ref": "https://storage.example.com/releases/2026.03.06.1/mainTemplate.json",
                      "deployment_scope": "resource_group",
                      "execution_settings": {
                        "arm_mode": "incremental",
                        "what_if_on_canary": true,
                        "verify_after_deploy": true
                      },
                      "parameter_defaults": {
                        "softwareVersion": "2026.03.06.1",
                        "dataModelVersion": "6"
                      },
                      "release_notes": "Managed app release 1",
                      "verification_hints": [
                        "Verify the landing page shows 2026.03.06.1"
                      ]
                    },
                    {
                      "publication_status": "published",
                      "template_spec_id": "/subscriptions/test/resourceGroups/rg/providers/Microsoft.Resources/templateSpecs/demo",
                      "template_spec_version": "2026.03.06.1",
                      "template_spec_version_id": "/subscriptions/test/resourceGroups/rg/providers/Microsoft.Resources/templateSpecs/demo/versions/2026.03.06.1",
                      "source_type": "template_spec",
                      "deployment_scope": "resource_group",
                      "parameter_defaults": {
                        "softwareVersion": "2026.03.06.1",
                        "dataModelVersion": "6"
                      },
                      "release_notes": "Template spec fallback release"
                    },
                    {
                      "publication_status": "published",
                      "source_ref": "github://cvonderheid/mappo-managed-app/managed-app/mainTemplate.json",
                      "source_version": "2026.03.07.1",
                      "source_type": "deployment_stack",
                      "source_version_ref": "https://storage.example.com/releases/2026.03.07.1/mainTemplate.json",
                      "deployment_scope": "resource_group",
                      "parameter_defaults": {
                        "softwareVersion": "2026.03.07.1",
                        "dataModelVersion": "7"
                      },
                      "release_notes": "Managed app release 2",
                      "verification_hints": [
                        "Verify the landing page shows 2026.03.07.1"
                      ]
                    },
                    {
                      "publication_status": "draft",
                      "source_ref": "github://cvonderheid/mappo-managed-app/managed-app/mainTemplate.json",
                      "source_version": "2026.03.08.1",
                      "source_type": "deployment_stack",
                      "deployment_scope": "resource_group",
                      "parameter_defaults": {
                        "softwareVersion": "2026.03.08.1",
                        "dataModelVersion": "8"
                      }
                    }
                  ]
                }
                """;
                }
            };
        }
    }
}
