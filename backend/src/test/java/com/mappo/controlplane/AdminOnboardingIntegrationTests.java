package com.mappo.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AdminOnboardingIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void onboardingWithoutTargetIdUsesGeneratedTargetId() throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-admin-generated-001");
        event.put("eventType", "subscription_purchased");
        event.put("tenantId", "11111111-1111-1111-1111-111111111111");
        event.put("subscriptionId", "22222222-2222-2222-2222-222222222222");
        event.put("displayName", "Generated Target");
        event.put("containerAppResourceId", "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-generated/providers/Microsoft.App/containerApps/ca-generated");
        event.put("managedResourceGroupId", "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-generated");
        event.put("containerAppName", "ca-generated");
        event.put("customerName", "Generated Customer");
        event.put("tags", Map.of("ring", "canary", "region", "eastus", "tier", "gold", "environment", "prod"));
        event.put("metadata", Map.of("source", "marketplace-forwarder", "marketplacePayloadId", "mp-generated-001"));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("evt-admin-generated-001"))
            .andExpect(jsonPath("$.status").value("applied"))
            .andReturn();
        String generatedTargetId = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("targetId").asText();

        assertThat(generatedTargetId).matches("tgt-[0-9a-f]{16}");

        mockMvc.perform(get("/api/v1/targets/page").queryParam("targetId", generatedTargetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(generatedTargetId))
            .andExpect(jsonPath("$.items[0].customerName").value("Generated Customer"));
    }

    @Test
    void registrationPatchUpdatesFleetSourceOfTruth() throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-admin-001");
        event.put("eventType", "subscription_purchased");
        event.put("tenantId", "11111111-1111-1111-1111-111111111111");
        event.put("subscriptionId", "22222222-2222-2222-2222-222222222222");
        event.put("targetId", "target-admin-01");
        event.put("displayName", "Target Admin 01");
        event.put("containerAppResourceId", "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-demo/providers/Microsoft.App/containerApps/ca-target-admin-01");
        event.put("managedResourceGroupId", "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-demo");
        event.put("containerAppName", "ca-target-admin-01");
        event.put("customerName", "Acme Original");
        event.put("tags", Map.of("ring", "canary", "region", "eastus", "tier", "gold", "environment", "prod"));
        event.put("metadata", Map.of("source", "marketplace-forwarder", "marketplacePayloadId", "mp-evt-001"));

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("evt-admin-001"))
            .andExpect(jsonPath("$.status").value("applied"));

        mockMvc.perform(get("/api/v1/targets/page"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("target-admin-01"))
            .andExpect(jsonPath("$.items[0].customerName").value("Acme Original"));

        mockMvc.perform(patch("/api/v1/admin/onboarding/registrations/target-admin-01")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "customerName": "Acme Updated",
                      "metadata": {
                        "deploymentStackName": "mappo-stack-acme-01",
                        "registryAuthMode": "shared_service_principal_secret",
                        "registryServer": "acrmappodemo.azurecr.io",
                        "registryUsername": "00000000-0000-0000-0000-000000000123",
                        "registryPasswordSecretName": "publisher-acr-pull",
                        "executionConfig": {
                          "pipeline.organization": "contoso",
                          "pipeline.project": "platform",
                          "pipeline.id": "42"
                        }
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetId").value("target-admin-01"))
            .andExpect(jsonPath("$.customerName").value("Acme Updated"))
            .andExpect(jsonPath("$.metadata.deploymentStackName").value("mappo-stack-acme-01"))
            .andExpect(jsonPath("$.metadata.registryAuthMode").value("shared_service_principal_secret"))
            .andExpect(jsonPath("$.metadata.executionConfig['pipeline.organization']").value("contoso"))
            .andExpect(jsonPath("$.metadata.executionConfig['pipeline.project']").value("platform"))
            .andExpect(jsonPath("$.metadata.executionConfig['pipeline.id']").value("42"));

        mockMvc.perform(get("/api/v1/targets/page"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].customerName").value("Acme Updated"));

        Map<String, Object> forwarderLog = new LinkedHashMap<>();
        forwarderLog.put("logId", "log-admin-001");
        forwarderLog.put("message", "forwarder rejected event");
        forwarderLog.put("eventId", "evt-admin-001");
        forwarderLog.put("eventType", "subscription_purchased");
        forwarderLog.put("details", Map.of("detail", "customer mapping missing", "backendResponse", "{\"detail\":\"bad request\"}"));

        mockMvc.perform(post("/api/v1/admin/onboarding/forwarder-logs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(forwarderLog)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"));

        mockMvc.perform(get("/api/v1/admin/onboarding/registrations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].metadata.containerAppName").value("ca-target-admin-01"))
            .andExpect(jsonPath("$.items[0].metadata.source").value("marketplace-forwarder"))
            .andExpect(jsonPath("$.items[0].metadata.deploymentStackName").value("mappo-stack-acme-01"))
            .andExpect(jsonPath("$.items[0].metadata.registryAuthMode").value("shared_service_principal_secret"))
            .andExpect(jsonPath("$.items[0].metadata.registryServer").value("acrmappodemo.azurecr.io"))
            .andExpect(jsonPath("$.items[0].metadata.registryUsername").value("00000000-0000-0000-0000-000000000123"))
            .andExpect(jsonPath("$.items[0].metadata.registryPasswordSecretName").value("publisher-acr-pull"))
            .andExpect(jsonPath("$.items[0].metadata.executionConfig['pipeline.organization']").value("contoso"))
            .andExpect(jsonPath("$.items[0].metadata.executionConfig['pipeline.project']").value("platform"))
            .andExpect(jsonPath("$.items[0].metadata.executionConfig['pipeline.id']").value("42"));

        mockMvc.perform(get("/api/v1/admin/onboarding/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].payload.registrationSource").value("marketplace-forwarder"))
            .andExpect(jsonPath("$.items[0].payload.marketplacePayloadId").value("mp-evt-001"));

        mockMvc.perform(get("/api/v1/admin/onboarding/forwarder-logs/page"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].details.detail").value("customer mapping missing"))
            .andExpect(jsonPath("$.items[0].details.backendResponse").value("{\"detail\":\"bad request\"}"));
    }
}
