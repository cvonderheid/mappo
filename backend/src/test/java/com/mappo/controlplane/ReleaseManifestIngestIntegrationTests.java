package com.mappo.controlplane;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mappo.controlplane.service.release.ReleaseManifestSourceClient;
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
                      "repo": "cvonderheid/mappo-managed-app",
                      "path": "releases/releases.manifest.json",
                      "ref": "main"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifestReleaseCount").value(2))
            .andExpect(jsonPath("$.createdCount").value(2))
            .andExpect(jsonPath("$.skippedCount").value(0))
            .andExpect(jsonPath("$.createdReleaseIds.length()").value(2));

        mockMvc.perform(post("/api/v1/admin/releases/ingest/github")
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifestReleaseCount").value(2))
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.skippedCount").value(2));

        mockMvc.perform(get("/api/v1/releases"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].sourceRef").value("/subscriptions/demo/resourceGroups/rg-demo/providers/Microsoft.Resources/templateSpecs/mappo-managed-app"))
            .andExpect(jsonPath("$[*].sourceVersion", containsInAnyOrder("2026.03.06.1", "2026.03.07.1")));
    }

    @TestConfiguration
    static class ReleaseManifestClientStubConfig {

        @Bean
        @Primary
        ReleaseManifestSourceClient releaseManifestSourceClient() {
            return (repo, path, ref) -> """
                {
                  "releases": [
                    {
                      "source_ref": "/subscriptions/demo/resourceGroups/rg-demo/providers/Microsoft.Resources/templateSpecs/mappo-managed-app",
                      "source_version": "2026.03.06.1",
                      "source_type": "template_spec",
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
                      "source_ref": "/subscriptions/demo/resourceGroups/rg-demo/providers/Microsoft.Resources/templateSpecs/mappo-managed-app",
                      "source_version": "2026.03.07.1",
                      "source_type": "template_spec",
                      "deployment_scope": "resource_group",
                      "parameter_defaults": {
                        "softwareVersion": "2026.03.07.1",
                        "dataModelVersion": "7"
                      },
                      "release_notes": "Managed app release 2",
                      "verification_hints": [
                        "Verify the landing page shows 2026.03.07.1"
                      ]
                    }
                  ]
                }
                """;
        }
    }
}
