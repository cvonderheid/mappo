package com.mappo.controlplane;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.health.TargetVerificationProvider;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ExternalExecutionHandleRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.TargetVerificationResultRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.mappo.controlplane.integrations.azure.deploymentstack.DeploymentStackExecutor;
import com.mappo.controlplane.domain.execution.TargetDeploymentOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class DeploymentStackExecutionIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createRunUsesDeploymentStackExecutorWhenAzureConfigIsPresent() throws Exception {
        registerTarget("target-stack-01", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        Map<String, Object> releaseRequest = new LinkedHashMap<>();
        releaseRequest.put("projectId", BuiltinProjects.AZURE_MANAGED_APP_DEPLOYMENT_STACK);
        releaseRequest.put("sourceRef", "github://cvonderheid/mappo-managed-app/managed-app/mainTemplate.json");
        releaseRequest.put("sourceVersion", "2026.03.08.1");
        releaseRequest.put("sourceType", "deployment_stack");
        releaseRequest.put("sourceVersionRef", "https://storage.example.com/releases/2026.03.08.1/mainTemplate.json");
        releaseRequest.put("deploymentScope", "resource_group");
        releaseRequest.put("releaseNotes", "deployment stack execution test");

        String releaseId = objectMapper.readTree(mockMvc.perform(post("/api/v1/releases")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(releaseRequest)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsByteArray()
        ).get("id").asText();

        Map<String, Object> runRequest = new LinkedHashMap<>();
        runRequest.put("releaseId", releaseId);
        runRequest.put("targetIds", List.of("target-stack-01"));
        runRequest.put("strategyMode", "all_at_once");
        runRequest.put("concurrency", 1);

        var runResponse = mockMvc.perform(post("/api/v1/runs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(runRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.targetRecords[0].status").value("QUEUED"))
            .andReturn();

        String runId = objectMapper.readTree(runResponse.getResponse().getContentAsByteArray()).get("id").asText();
        awaitTerminalRun(runId);
    }

    private void awaitTerminalRun(String runId) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            var runResult = mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andReturn();
            String runStatus = objectMapper.readTree(runResult.getResponse().getContentAsByteArray()).get("status").asText();
            if (!"running".equals(runStatus)) {
                mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("succeeded"))
                    .andExpect(jsonPath("$.guardrailWarnings").isEmpty())
                    .andExpect(jsonPath("$.targetRecords[0].status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.targetRecords[0].externalExecutionHandle.provider").value("azure_deployment_stack"))
                    .andExpect(jsonPath("$.targetRecords[0].externalExecutionHandle.executionName").value("mappo-stack-target-stack-01"))
                    .andExpect(jsonPath("$.targetRecords[0].stages[1].message").value(containsString("updating deployment stack scope")))
                    .andExpect(jsonPath("$.targetRecords[0].stages[2].message").value("Deployment Stack mappo-stack-target-stack-01 succeeded."))
                    .andExpect(jsonPath("$.targetRecords[0].stages[3].message").value("Runtime verification passed: Runtime responded with HTTP 200."))
                    .andExpect(jsonPath("$.targetRecords[0].stages[4].stage").value("SUCCEEDED"));
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("deployment stack run did not reach terminal state");
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

    @TestConfiguration
    static class DeploymentStackExecutorStubConfig {

        @Bean
        @Primary
        DeploymentStackExecutor deploymentStackExecutor() {
            return new StubDeploymentStackExecutor();
        }

        @Bean
        @Primary
        TargetVerificationProvider targetVerificationProvider() {
            return new StubTargetVerificationProvider();
        }
    }

    static class StubDeploymentStackExecutor implements DeploymentStackExecutor {

        @Override
        public TargetDeploymentOutcome deploy(
            String runId,
            ProjectDefinition project,
            ReleaseRecord release,
            TargetExecutionContextRecord target,
            ResolvedTargetAccessContext accessContext
        ) {
            String stackName = target.deploymentStackName() == null || target.deploymentStackName().isBlank()
                ? "mappo-stack-target-stack-01"
                : target.deploymentStackName();
            return new TargetDeploymentOutcome(
                "corr-" + runId + "-" + target.targetId() + "-deploy",
                "Deployment Stack " + stackName + " succeeded.",
                "",
                new ExternalExecutionHandleRecord(
                    "azure_deployment_stack",
                    "stack/" + stackName,
                    stackName,
                    "succeeded",
                    null,
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC)
                )
            );
        }
    }

    static class StubTargetVerificationProvider implements TargetVerificationProvider {

        @Override
        public boolean supports(
            ProjectDefinition project,
            ReleaseRecord release,
            TargetRecord target,
            TargetExecutionContextRecord context,
            boolean runtimeConfigured
        ) {
            return project.runtimeHealthProvider() == ProjectRuntimeHealthProviderType.azure_container_app_http;
        }

        @Override
        public TargetVerificationResultRecord verify(
            ProjectDefinition project,
            ReleaseRecord release,
            TargetRecord target,
            TargetExecutionContextRecord context,
            boolean runtimeConfigured
        ) {
            return TargetVerificationResultRecord.success("Runtime verification passed: Runtime responded with HTTP 200.");
        }
    }
}
