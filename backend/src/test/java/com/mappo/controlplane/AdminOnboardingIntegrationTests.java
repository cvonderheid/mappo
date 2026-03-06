package com.mappo.controlplane;

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

        mockMvc.perform(get("/api/v1/targets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("target-admin-01"))
            .andExpect(jsonPath("$[0].customerName").value("Acme Original"));

        mockMvc.perform(patch("/api/v1/admin/onboarding/registrations/target-admin-01")
                .contentType(APPLICATION_JSON)
                .content("{\"customerName\":\"Acme Updated\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetId").value("target-admin-01"))
            .andExpect(jsonPath("$.customerName").value("Acme Updated"));

        mockMvc.perform(get("/api/v1/targets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].customerName").value("Acme Updated"));

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

        mockMvc.perform(get("/api/v1/admin/onboarding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrations[0].metadata.containerAppName").value("ca-target-admin-01"))
            .andExpect(jsonPath("$.registrations[0].metadata.source").value("marketplace-forwarder"))
            .andExpect(jsonPath("$.events[0].payload.registrationSource").value("marketplace-forwarder"))
            .andExpect(jsonPath("$.events[0].payload.marketplacePayloadId").value("mp-evt-001"))
            .andExpect(jsonPath("$.forwarderLogs[0].details.detail").value("customer mapping missing"))
            .andExpect(jsonPath("$.forwarderLogs[0].details.backendResponse").value("{\"detail\":\"bad request\"}"));
    }
}
