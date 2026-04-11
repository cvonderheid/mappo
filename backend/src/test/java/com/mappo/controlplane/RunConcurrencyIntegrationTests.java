package com.mappo.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.domain.execution.TargetDeploymentOutcome;
import com.mappo.controlplane.integrations.azure.templatespec.TemplateSpecExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

@SpringBootTest(properties = {
    "mappo.azure.tenant-id=00000000-0000-0000-0000-000000000001",
    "mappo.azure.client-id=00000000-0000-0000-0000-000000000002",
    "mappo.azure.client-secret=test-secret"
})
class RunConcurrencyIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        TemplateSpecTrackingStub.reset();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void allAtOnceHonorsConfiguredConcurrency() throws Exception {
        registerTarget("target-parallel-01", "canary", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        registerTarget("target-parallel-02", "prod", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2");
        String releaseId = createTemplateSpecRelease("2026.03.09.1");

        Map<String, Object> runRequest = new LinkedHashMap<>();
        runRequest.put("releaseId", releaseId);
        runRequest.put("targetIds", List.of("target-parallel-01", "target-parallel-02"));
        runRequest.put("strategyMode", "all_at_once");
        runRequest.put("concurrency", 2);

        TemplateSpecTrackingStub.enableConcurrencyProbe(2);
        String runId = objectMapper.readTree(mockMvc.perform(post("/api/v1/runs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(runRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("running"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray()).get("id").asText();

        awaitTerminalRun(runId);

        assertThat(TemplateSpecTrackingStub.maxConcurrency()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void wavesRespectWaveOrder() throws Exception {
        registerTarget("target-wave-canary", "canary", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3");
        registerTarget("target-wave-prod", "prod", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4");
        String releaseId = createTemplateSpecRelease("2026.03.09.2");

        Map<String, Object> runRequest = new LinkedHashMap<>();
        runRequest.put("releaseId", releaseId);
        runRequest.put("targetIds", List.of("target-wave-canary", "target-wave-prod"));
        runRequest.put("strategyMode", "waves");
        runRequest.put("concurrency", 2);

        String runId = objectMapper.readTree(mockMvc.perform(post("/api/v1/runs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(runRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("running"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray()).get("id").asText();

        awaitTerminalRun(runId);

        assertThat(TemplateSpecTrackingStub.invocationOrder())
            .containsExactly("target-wave-canary", "target-wave-prod");
    }

    private String createTemplateSpecRelease(String version) throws Exception {
        Map<String, Object> releaseRequest = new LinkedHashMap<>();
        releaseRequest.put("projectId", BuiltinProjects.AZURE_MANAGED_APP_TEMPLATE_SPEC);
        releaseRequest.put("sourceRef", "/subscriptions/00000000-0000-0000-0000-000000000001/resourceGroups/rg-mappo-def/providers/Microsoft.Resources/templateSpecs/mappo-app");
        releaseRequest.put("sourceVersion", version);
        releaseRequest.put("sourceType", "template_spec");
        releaseRequest.put("deploymentScope", "resource_group");
        releaseRequest.put("releaseNotes", "concurrency test release");

        return objectMapper.readTree(mockMvc.perform(post("/api/v1/releases")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(releaseRequest)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsByteArray()).get("id").asText();
    }

    private void registerTarget(String targetId, String ring, String tenantId, String subscriptionId) throws Exception {
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
        event.put("tags", Map.of("ring", ring, "region", "eastus", "tier", "gold", "environment", "prod"));

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"));
    }

    private void awaitTerminalRun(String runId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            var runResult = mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andReturn();
            String runStatus = objectMapper.readTree(runResult.getResponse().getContentAsByteArray()).get("status").asText();
            if (!"running".equals(runStatus)) {
                mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("succeeded"));
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("run did not reach a terminal state: " + runId);
    }

    @TestConfiguration
    static class TemplateSpecExecutorStubConfig {

        @Bean
        @Primary
        TemplateSpecExecutor templateSpecExecutor() {
            return new TemplateSpecTrackingStub();
        }
    }

    static class TemplateSpecTrackingStub implements TemplateSpecExecutor {

        private static final AtomicInteger ACTIVE = new AtomicInteger();
        private static final AtomicInteger MAX_CONCURRENCY = new AtomicInteger();
        private static final List<String> INVOCATION_ORDER = new CopyOnWriteArrayList<>();
        private static volatile CountDownLatch concurrencyProbe;

        static void reset() {
            ACTIVE.set(0);
            MAX_CONCURRENCY.set(0);
            INVOCATION_ORDER.clear();
            concurrencyProbe = null;
        }

        static void enableConcurrencyProbe(int targetCount) {
            concurrencyProbe = new CountDownLatch(targetCount);
        }

        static int maxConcurrency() {
            return MAX_CONCURRENCY.get();
        }

        static List<String> invocationOrder() {
            return new ArrayList<>(INVOCATION_ORDER);
        }

        @Override
        public TargetDeploymentOutcome deploy(
            String runId,
            ProjectDefinition project,
            ReleaseRecord release,
            TargetExecutionContextRecord target,
            ResolvedTargetAccessContext accessContext
        ) {
            INVOCATION_ORDER.add(target.targetId());
            int current = ACTIVE.incrementAndGet();
            MAX_CONCURRENCY.accumulateAndGet(current, Math::max);
            CountDownLatch probe = concurrencyProbe;
            try {
                if (probe != null) {
                    probe.countDown();
                    probe.await(1, TimeUnit.SECONDS);
                }
                Thread.sleep(150);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("stub execution interrupted", error);
            } finally {
                ACTIVE.decrementAndGet();
            }
            return new TargetDeploymentOutcome(
                "corr-" + runId + "-" + target.targetId() + "-deploy",
                "Template Spec deployment stub-template-spec-deployment succeeded.",
                ""
            );
        }
    }
}
