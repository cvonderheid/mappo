package com.mappo.controlplane;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mappo.controlplane.application.release.ReleaseManifestSourceClient;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
    "mappo.managed-app-release.webhook-secret=test-github-secret"
})
class ReleaseManifestWebhookIntegrationTests extends PostgresIntegrationTestBase {

    private static final String WEBHOOK_SECRET = "test-github-secret";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void webhookPushIngestsManifestRowsWhenManifestChanges() throws Exception {
        String deliveryId = "delivery-created-001";
        String payload = """
            {
              "ref": "refs/heads/main",
              "repository": {
                "full_name": "example-org/mappo-release-catalog"
              },
              "commits": [
                {
                  "modified": [
                    "releases/releases.manifest.json"
                  ]
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/v1/release-ingest/endpoints/{endpointId}/webhooks/github", githubReleaseEndpointId())
                .contentType(APPLICATION_JSON)
                .content(payload)
                .header("x-github-event", "push")
                .header("x-github-delivery", deliveryId)
                .header("x-hub-signature-256", githubSignature(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifestReleaseCount").value(2))
            .andExpect(jsonPath("$.createdCount").value(2))
            .andExpect(jsonPath("$.skippedCount").value(0))
            .andExpect(jsonPath("$.ignoredCount").value(0));

        mockMvc.perform(get("/api/v1/releases"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/admin/releases/webhook-deliveries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].externalDeliveryId").value(deliveryId))
            .andExpect(jsonPath("$.items[0].status").value("applied"))
            .andExpect(jsonPath("$.items[0].repo").value("example-org/mappo-release-catalog"))
            .andExpect(jsonPath("$.items[0].ref").value("main"))
            .andExpect(jsonPath("$.items[0].createdCount").value(2))
            .andExpect(jsonPath("$.items[0].changedPaths[0]").value("releases/releases.manifest.json"));
    }

    @Test
    void webhookPushIgnoresIrrelevantFileChanges() throws Exception {
        String deliveryId = "delivery-skipped-001";
        String payload = """
            {
              "ref": "refs/heads/main",
              "repository": {
                "full_name": "example-org/mappo-release-catalog"
              },
              "commits": [
                {
                  "modified": [
                    "README.md"
                  ]
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/v1/release-ingest/endpoints/{endpointId}/webhooks/github", githubReleaseEndpointId())
                .contentType(APPLICATION_JSON)
                .content(payload)
                .header("x-github-event", "push")
                .header("x-github-delivery", deliveryId)
                .header("x-hub-signature-256", githubSignature(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifestReleaseCount").value(0))
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.skippedCount").value(0))
            .andExpect(jsonPath("$.ignoredCount").value(0));

        mockMvc.perform(get("/api/v1/admin/releases/webhook-deliveries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].externalDeliveryId").value(deliveryId))
            .andExpect(jsonPath("$.items[0].status").value("skipped"))
            .andExpect(jsonPath("$.items[0].message")
                .value("Ignored webhook push because the managed-app release manifest did not change."));
    }

    @Test
    void webhookRejectsInvalidSignature() throws Exception {
        String payload = """
            {
              "ref": "refs/heads/main",
              "repository": {
                "full_name": "example-org/mappo-release-catalog"
              },
              "commits": []
            }
            """;

        mockMvc.perform(post("/api/v1/release-ingest/endpoints/{endpointId}/webhooks/github", githubReleaseEndpointId())
                .contentType(APPLICATION_JSON)
                .content(payload)
                .header("x-github-event", "push")
                .header("x-hub-signature-256", "sha256=invalid"))
            .andExpect(status().isUnauthorized());
    }

    private String githubSignature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return "sha256=" + builder;
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
                      "source_ref": "github://example-org/mappo-release-catalog/managed-app/mainTemplate.json",
                      "source_version": "2026.03.06.1",
                      "source_type": "deployment_stack",
                      "source_version_ref": "https://storage.example.com/releases/2026.03.06.1/mainTemplate.json",
                      "parameter_defaults": {
                        "softwareVersion": "2026.03.06.1",
                        "dataModelVersion": "6"
                      }
                    },
                    {
                      "source_ref": "github://example-org/mappo-release-catalog/managed-app/mainTemplate.json",
                      "source_version": "2026.03.07.1",
                      "source_type": "deployment_stack",
                      "source_version_ref": "https://storage.example.com/releases/2026.03.07.1/mainTemplate.json",
                      "parameter_defaults": {
                        "softwareVersion": "2026.03.07.1",
                        "dataModelVersion": "7"
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
