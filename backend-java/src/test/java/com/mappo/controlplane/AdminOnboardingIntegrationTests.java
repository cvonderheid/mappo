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
        event.put("event_id", "evt-admin-001");
        event.put("event_type", "subscription_purchased");
        event.put("tenant_id", "11111111-1111-1111-1111-111111111111");
        event.put("subscription_id", "22222222-2222-2222-2222-222222222222");
        event.put("target_id", "target-admin-01");
        event.put("display_name", "Target Admin 01");
        event.put("container_app_resource_id", "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-demo/providers/Microsoft.App/containerApps/ca-target-admin-01");
        event.put("managed_resource_group_id", "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-demo");
        event.put("customer_name", "Acme Original");
        event.put("tags", Map.of("ring", "canary", "region", "eastus", "tier", "gold", "environment", "prod"));

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.event_id").value("evt-admin-001"))
            .andExpect(jsonPath("$.status").value("applied"));

        mockMvc.perform(get("/api/v1/targets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("target-admin-01"))
            .andExpect(jsonPath("$[0].customer_name").value("Acme Original"));

        mockMvc.perform(patch("/api/v1/admin/onboarding/registrations/target-admin-01")
                .contentType(APPLICATION_JSON)
                .content("{\"customer_name\":\"Acme Updated\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.target_id").value("target-admin-01"))
            .andExpect(jsonPath("$.customer_name").value("Acme Updated"));

        mockMvc.perform(get("/api/v1/targets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].customer_name").value("Acme Updated"));
    }
}
