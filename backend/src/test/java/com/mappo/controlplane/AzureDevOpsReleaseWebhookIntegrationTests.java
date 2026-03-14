package com.mappo.controlplane;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mappo.controlplane.domain.project.BuiltinProjects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
    "mappo.azure-devops.webhook-secret=test-ado-webhook-secret"
})
class AzureDevOpsReleaseWebhookIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void webhookCreatesPipelineReleaseForAdoProject() throws Exception {
        String payload = """
            {
              "eventType": "ms.vss-pipelines.run-state-changed-event",
              "resource": {
                "pipeline": {
                  "id": 1,
                  "name": "deploy-demo-appservice"
                },
                "run": {
                  "id": 320,
                  "name": "2026.03.13.4",
                  "state": "completed",
                  "result": "succeeded",
                  "url": "https://dev.azure.com/pg123/demo-app-service/_apis/pipelines/1/runs/320",
                  "_links": {
                    "web": {
                      "href": "https://dev.azure.com/pg123/demo-app-service/_build/results?buildId=320&view=results"
                    }
                  },
                  "resources": {
                    "repositories": {
                      "self": {
                        "refName": "refs/heads/main"
                      }
                    }
                  }
                }
              },
              "resourceContainers": {
                "project": {
                  "baseUrl": "https://dev.azure.com/pg123",
                  "name": "demo-app-service"
                }
              }
            }
            """;

        mockMvc.perform(post("/api/v1/admin/releases/webhooks/ado")
                .queryParam("token", "test-ado-webhook-secret")
                .queryParam("projectId", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE)
                .header("x-vss-event", "ms.vss-pipelines.run-state-changed-event")
                .header("x-vss-deliveryid", "ado-delivery-001")
                .contentType(APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifestReleaseCount").value(1))
            .andExpect(jsonPath("$.createdCount").value(1))
            .andExpect(jsonPath("$.skippedCount").value(0))
            .andExpect(jsonPath("$.ignoredCount").value(0));

        mockMvc.perform(get("/api/v1/releases")
                .queryParam("projectId", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sourceVersion").value("2026.03.13.4"))
            .andExpect(jsonPath("$[0].sourceRef").value("ado://pg123/demo-app-service/pipelines/1"))
            .andExpect(jsonPath("$[0].sourceType").value("external_deployment_inputs"));

        mockMvc.perform(get("/api/v1/admin/releases/webhook-deliveries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].externalDeliveryId").value("ado-delivery-001"))
            .andExpect(jsonPath("$.items[0].status").value("applied"))
            .andExpect(jsonPath("$.items[0].repo").value("ado://pg123/demo-app-service"))
            .andExpect(jsonPath("$.items[0].ref").value("main"));
    }

    @Test
    void webhookSkipsNonSucceededRuns() throws Exception {
        String payload = """
            {
              "eventType": "ms.vss-pipelines.run-state-changed-event",
              "resource": {
                "pipeline": {
                  "id": 1
                },
                "run": {
                  "id": 321,
                  "name": "2026.03.13.5",
                  "state": "completed",
                  "result": "failed",
                  "resources": {
                    "repositories": {
                      "self": {
                        "refName": "refs/heads/main"
                      }
                    }
                  }
                }
              },
              "resourceContainers": {
                "project": {
                  "baseUrl": "https://dev.azure.com/pg123",
                  "name": "demo-app-service"
                }
              }
            }
            """;

        mockMvc.perform(post("/api/v1/admin/releases/webhooks/ado")
                .queryParam("token", "test-ado-webhook-secret")
                .queryParam("projectId", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE)
                .contentType(APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.manifestReleaseCount").value(0));
    }

    @Test
    void webhookRejectsInvalidToken() throws Exception {
        String payload = """
            {
              "eventType": "ms.vss-pipelines.run-state-changed-event",
              "resource": {
                "pipeline": {
                  "id": 1
                },
                "run": {
                  "id": 322,
                  "name": "2026.03.13.6",
                  "state": "completed",
                  "result": "succeeded"
                }
              }
            }
            """;

        mockMvc.perform(post("/api/v1/admin/releases/webhooks/ado")
                .queryParam("token", "wrong-secret")
                .queryParam("projectId", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE)
                .contentType(APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());
    }
}
