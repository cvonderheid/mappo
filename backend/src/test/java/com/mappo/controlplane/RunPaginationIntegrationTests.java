package com.mappo.controlplane;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.domain.project.BuiltinProjects;
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

@SpringBootTest(properties = {
    "mappo.azure.tenant-id=",
    "mappo.azure.client-id=",
    "mappo.azure.client-secret=",
    "MAPPO_AZURE_TENANT_ID=",
    "MAPPO_AZURE_CLIENT_ID=",
    "MAPPO_AZURE_CLIENT_SECRET="
})
class RunPaginationIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void listRunsReturnsPageMetadataAndSupportsFilters() throws Exception {
        registerTarget("target-page-01", "55555555-5555-5555-5555-555555555555", "66666666-6666-6666-6666-666666666666");

        String releaseIdOne = createRelease("2026.03.09.1");
        String releaseIdTwo = createRelease("2026.03.09.2");
        String runIdOne = createRun(releaseIdOne);
        String runIdTwo = createRun(releaseIdTwo);

        awaitTerminalRun(runIdOne);
        awaitTerminalRun(runIdTwo);

        mockMvc.perform(get("/api/v1/runs?page=0&size=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.page.page").value(0))
            .andExpect(jsonPath("$.page.size").value(1))
            .andExpect(jsonPath("$.page.totalItems").value(2))
            .andExpect(jsonPath("$.page.totalPages").value(2))
            .andExpect(jsonPath("$.activeRunCount").value(0));

        mockMvc.perform(get("/api/v1/runs").param("releaseId", releaseIdTwo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].releaseId").value(releaseIdTwo));

        mockMvc.perform(get("/api/v1/runs").param("runId", runIdOne))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(runIdOne));

        mockMvc.perform(get("/api/v1/runs").param("status", "succeeded"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.page.totalItems").value(2));
    }

    private String createRelease(String version) throws Exception {
        Map<String, Object> releaseRequest = new LinkedHashMap<>();
        releaseRequest.put("projectId", BuiltinProjects.AZURE_MANAGED_APP_TEMPLATE_SPEC);
        releaseRequest.put("sourceRef", "/subscriptions/00000000-0000-0000-0000-000000000001/resourceGroups/rg-mappo-def/providers/Microsoft.Resources/templateSpecs/mappo-app");
        releaseRequest.put("sourceVersion", version);
        releaseRequest.put("sourceType", "template_spec");
        releaseRequest.put("deploymentScope", "resource_group");
        releaseRequest.put("releaseNotes", "pagination test release " + version);

        MvcResult releaseResponse = mockMvc.perform(post("/api/v1/releases")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(releaseRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(releaseResponse.getResponse().getContentAsByteArray()).get("id").asText();
    }

    private String createRun(String releaseId) throws Exception {
        Map<String, Object> runRequest = new LinkedHashMap<>();
        runRequest.put("releaseId", releaseId);
        runRequest.put("targetIds", List.of("target-page-01"));
        runRequest.put("strategyMode", "all_at_once");
        runRequest.put("concurrency", 1);

        MvcResult runResponse = mockMvc.perform(post("/api/v1/runs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(runRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(runResponse.getResponse().getContentAsByteArray()).get("id").asText();
    }

    private void awaitTerminalRun(String runId) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andReturn();
            String status = objectMapper.readTree(result.getResponse().getContentAsString()).get("status").asText();
            if (!"running".equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run did not reach a terminal state: " + runId);
    }

    private void registerTarget(String targetId, String tenantId, String subscriptionId) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-" + targetId);
        event.put("eventType", "subscription_purchased");
        event.put("tenantId", tenantId);
        event.put("subscriptionId", subscriptionId);
        event.put("targetId", targetId);
        event.put("displayName", targetId);
        event.put("projectId", BuiltinProjects.AZURE_MANAGED_APP_TEMPLATE_SPEC);
        event.put("containerAppResourceId", "/subscriptions/" + subscriptionId + "/resourceGroups/rg-" + targetId + "/providers/Microsoft.App/containerApps/ca-" + targetId);
        event.put("managedResourceGroupId", "/subscriptions/" + subscriptionId + "/resourceGroups/rg-" + targetId);
        event.put("customerName", "Demo Customer");
        event.put("tags", Map.of("ring", "canary", "region", "eastus", "tier", "gold", "environment", "prod"));

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"));
    }
}
