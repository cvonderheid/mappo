package com.mappo.controlplane;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.domain.health.TargetVerificationProvider;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.TargetVerificationResultRecord;
import com.mappo.controlplane.service.run.TargetDeploymentOutcome;
import com.mappo.controlplane.service.run.TemplateSpecExecutor;
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
class TemplateSpecExecutionIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createRunUsesTemplateSpecExecutorWhenAzureConfigIsPresent() throws Exception {
        registerTarget("target-template-01", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        Map<String, Object> releaseRequest = new LinkedHashMap<>();
        releaseRequest.put("projectId", BuiltinProjects.AZURE_MANAGED_APP_TEMPLATE_SPEC);
        releaseRequest.put("sourceRef", "/subscriptions/00000000-0000-0000-0000-000000000001/resourceGroups/rg-mappo-def/providers/Microsoft.Resources/templateSpecs/mappo-app");
        releaseRequest.put("sourceVersion", "2026.03.06.1");
        releaseRequest.put("sourceType", "template_spec");
        releaseRequest.put("deploymentScope", "resource_group");
        releaseRequest.put("releaseNotes", "template spec execution test");

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
        runRequest.put("targetIds", List.of("target-template-01"));
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
                    .andExpect(jsonPath("$.targetRecords[0].stages[1].message").value(containsString("deploying into resource group")))
                    .andExpect(jsonPath("$.targetRecords[0].stages[2].message").value("Template Spec deployment stub-template-spec-deployment succeeded."))
                    .andExpect(jsonPath("$.targetRecords[0].stages[3].message").value("Runtime verification passed: Runtime responded with HTTP 200."))
                    .andExpect(jsonPath("$.targetRecords[0].stages[4].stage").value("SUCCEEDED"));
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("template spec run did not reach terminal state");
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

    @TestConfiguration
    static class TemplateSpecExecutorStubConfig {

        @Bean
        @Primary
        TemplateSpecExecutor templateSpecExecutor() {
            return new StubTemplateSpecExecutor();
        }

        @Bean
        @Primary
        TargetVerificationProvider targetVerificationProvider() {
            return new StubTargetVerificationProvider();
        }
    }

    static class StubTemplateSpecExecutor implements TemplateSpecExecutor {

        @Override
        public TargetDeploymentOutcome deploy(
            String runId,
            ProjectDefinition project,
            ReleaseRecord release,
            TargetExecutionContextRecord target
        ) {
            return new TargetDeploymentOutcome(
                "corr-" + runId + "-" + target.targetId() + "-deploy",
                "Template Spec deployment stub-template-spec-deployment succeeded.",
                ""
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
            boolean azureConfigured
        ) {
            return project.runtimeHealthProvider() == ProjectRuntimeHealthProviderType.azure_container_app_http;
        }

        @Override
        public TargetVerificationResultRecord verify(
            ProjectDefinition project,
            ReleaseRecord release,
            TargetRecord target,
            TargetExecutionContextRecord context,
            boolean azureConfigured
        ) {
            return TargetVerificationResultRecord.success("Runtime verification passed: Runtime responded with HTTP 200.");
        }
    }
}
