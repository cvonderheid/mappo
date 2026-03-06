package com.mappo.controlplane;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.List;
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
class RunLifecycleIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createRunUpdatesTargetReleaseAndHealth() throws Exception {
        registerTarget("target-run-01", "33333333-3333-3333-3333-333333333333", "44444444-4444-4444-4444-444444444444");

        Map<String, Object> releaseRequest = new LinkedHashMap<>();
        releaseRequest.put("template_spec_id", "/subscriptions/00000000-0000-0000-0000-000000000001/resourceGroups/rg-mappo-def/providers/Microsoft.Resources/templateSpecs/mappo-app");
        releaseRequest.put("template_spec_version", "2026.03.05.1");
        releaseRequest.put("deployment_mode", "template_spec");
        releaseRequest.put("deployment_scope", "resource_group");
        releaseRequest.put("release_notes", "integration test release");

        MvcResult releaseResponse = mockMvc.perform(post("/api/v1/releases")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(releaseRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deployment_mode").value("template_spec"))
            .andReturn();

        Map<String, Object> releasePayload = objectMapper.readValue(
            releaseResponse.getResponse().getContentAsByteArray(),
            new TypeReference<>() {
            }
        );
        String releaseId = String.valueOf(releasePayload.get("id"));

        Map<String, Object> runRequest = new LinkedHashMap<>();
        runRequest.put("release_id", releaseId);
        runRequest.put("target_ids", List.of("target-run-01"));
        runRequest.put("strategy_mode", "all_at_once");
        runRequest.put("concurrency", 2);

        MvcResult runResponse = mockMvc.perform(post("/api/v1/runs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(runRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("succeeded"))
            .andExpect(jsonPath("$.execution_mode").value("template_spec"))
            .andExpect(jsonPath("$.target_records[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.guardrail_warnings[0]").value(containsString("azure sdk credentials")))
            .andReturn();

        Map<String, Object> runPayload = objectMapper.readValue(
            runResponse.getResponse().getContentAsByteArray(),
            new TypeReference<>() {
            }
        );
        String runId = String.valueOf(runPayload.get("id"));

        mockMvc.perform(get("/api/v1/targets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].last_deployed_release").value("2026.03.05.1"))
            .andExpect(jsonPath("$[0].health_status").value("healthy"));

        mockMvc.perform(post("/api/v1/runs/{runId}/resume", runId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("run is not resumable"));

        mockMvc.perform(post("/api/v1/runs/{runId}/retry-failed", runId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("run has no failed targets to retry"));
    }

    private void registerTarget(String targetId, String tenantId, String subscriptionId) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", "evt-" + targetId);
        event.put("event_type", "subscription_purchased");
        event.put("tenant_id", tenantId);
        event.put("subscription_id", subscriptionId);
        event.put("target_id", targetId);
        event.put("display_name", targetId);
        event.put("container_app_resource_id", "/subscriptions/" + subscriptionId + "/resourceGroups/rg-" + targetId + "/providers/Microsoft.App/containerApps/ca-" + targetId);
        event.put("managed_resource_group_id", "/subscriptions/" + subscriptionId + "/resourceGroups/rg-" + targetId);
        event.put("customer_name", "Demo Customer");
        event.put("tags", Map.of("ring", "canary", "region", "eastus", "tier", "gold", "environment", "prod"));

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"));
    }
}
