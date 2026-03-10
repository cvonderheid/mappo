package com.mappo.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.service.run.RunDispatchService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    "MAPPO_AZURE_CLIENT_SECRET=",
    "mappo.redis.worker-poll-timeout-ms=600000"
})
class RedisRunQueueIntegrationTests extends PostgresRedisIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RunDispatchService runDispatchService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MappoProperties properties;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        redisTemplate.delete(properties.getRedis().getQueueKey());
    }

    @Test
    void createRunQueuesInRedisAndClearsLeaseAfterExecution() throws Exception {
        registerTarget("target-redis-01", "33333333-3333-3333-3333-333333333333", "44444444-4444-4444-4444-444444444444");
        String releaseId = createTemplateSpecRelease("2026.03.09.3");

        Map<String, Object> runRequest = new LinkedHashMap<>();
        runRequest.put("releaseId", releaseId);
        runRequest.put("targetIds", List.of("target-redis-01"));
        runRequest.put("strategyMode", "all_at_once");
        runRequest.put("concurrency", 1);

        MvcResult runResponse = mockMvc.perform(post("/api/v1/runs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(runRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.targetRecords[0].status").value("QUEUED"))
            .andReturn();

        String runId = objectMapper.readTree(runResponse.getResponse().getContentAsByteArray()).get("id").asText();

        awaitTerminalRun(runId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("succeeded"))
            .andExpect(jsonPath("$.targetRecords[0].status").value("SUCCEEDED"));

        runDispatchService.drainQueuedRuns();

        assertThat(redisTemplate.opsForList().size(properties.getRedis().getQueueKey())).isZero();
        assertThat(redisTemplate.hasKey(properties.getRedis().getQueueLockPrefix() + runId)).isFalse();
        assertThat(redisTemplate.hasKey(properties.getRedis().getQueueDedupPrefix() + runId)).isFalse();
    }

    private String createTemplateSpecRelease(String version) throws Exception {
        Map<String, Object> releaseRequest = new LinkedHashMap<>();
        releaseRequest.put("sourceRef", "/subscriptions/00000000-0000-0000-0000-000000000001/resourceGroups/rg-mappo-def/providers/Microsoft.Resources/templateSpecs/mappo-app");
        releaseRequest.put("sourceVersion", version);
        releaseRequest.put("sourceType", "template_spec");
        releaseRequest.put("deploymentScope", "resource_group");
        releaseRequest.put("releaseNotes", "redis queue integration release");

        return objectMapper.readTree(mockMvc.perform(post("/api/v1/releases")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(releaseRequest)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsByteArray()).get("id").asText();
    }

    private void registerTarget(String targetId, String tenantId, String subscriptionId) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-" + targetId);
        event.put("eventType", "subscription_purchased");
        event.put("tenantId", tenantId);
        event.put("subscriptionId", subscriptionId);
        event.put("targetId", targetId);
        event.put("displayName", targetId);
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

    private org.springframework.test.web.servlet.ResultActions awaitTerminalRun(String runId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andReturn();
            String status = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("status").asText();
            if (!"running".equals(status)) {
                return mockMvc.perform(get("/api/v1/runs/{runId}", runId));
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run did not reach a terminal state: " + runId);
    }
}
