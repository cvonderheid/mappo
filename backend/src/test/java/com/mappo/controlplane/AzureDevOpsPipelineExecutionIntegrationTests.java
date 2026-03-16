package com.mappo.controlplane;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineClient;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDefinitionRecord;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDiscoveryInputs;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineInputs;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineRunRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
    "mappo.azure-devops.personal-access-token=test-pat",
    "mappo.azure-devops.run-poll-interval-ms=10",
    "mappo.azure-devops.run-poll-timeout-ms=500"
})
class AzureDevOpsPipelineExecutionIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createRunUsesAzureDevOpsPipelineDriver() throws Exception {
        configureAdoProjectForTest();
        registerAdoTarget(
            "target-ado-01",
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            Map.of(
                "resourceGroup", "rg-target-ado-01",
                "appServiceName", "app-target-ado-01"
            )
        );
        String releaseId = createAdoRelease("2026.03.13.1");

        String runId = createRun(releaseId, List.of("target-ado-01"));
        awaitTerminalRun(runId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("succeeded"))
            .andExpect(jsonPath("$.guardrailWarnings").isEmpty())
            .andExpect(jsonPath("$.targetRecords[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.targetRecords[0].externalExecutionHandle.provider").value("azure_devops_pipeline"))
            .andExpect(jsonPath("$.targetRecords[0].externalExecutionHandle.executionStatus").value("succeeded"))
            .andExpect(jsonPath("$.targetRecords[0].stages[1].message").value(containsString("Azure DevOps pipeline execution")))
            .andExpect(jsonPath("$.targetRecords[0].stages[2].message").value(containsString("pipeline run")))
            .andExpect(jsonPath("$.targetRecords[0].stages[3].message").value(
                "Verification passed: external pipeline deployment completed successfully."
            ));
    }

    @Test
    void createRunFailsValidationWhenAppServiceExecutionConfigIsMissing() throws Exception {
        configureAdoProjectForTest();
        registerAdoTarget(
            "target-ado-02",
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "cccccccc-cccc-cccc-cccc-cccccccccccc",
            Map.of("runtimeBaseUrl", "https://app-target-ado-02.azurewebsites.net")
        );
        String releaseId = createAdoRelease("2026.03.13.2");

        String runId = createRun(releaseId, List.of("target-ado-02"));
        awaitTerminalRun(runId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("failed"))
            .andExpect(jsonPath("$.targetRecords[0].status").value("FAILED"))
            .andExpect(jsonPath("$.targetRecords[0].stages[1].error.code").value("TARGET_CONFIGURATION_INVALID"))
            .andExpect(jsonPath("$.targetRecords[0].stages[1].error.message").value(
                "Target execution metadata is missing App Service deployment fields."
            ));
    }

    private String createAdoRelease(String version) throws Exception {
        Map<String, Object> releaseRequest = new LinkedHashMap<>();
        releaseRequest.put("projectId", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE);
        releaseRequest.put("sourceRef", "ado://pg123/demo-app-service/pipeline/1");
        releaseRequest.put("sourceVersion", version);
        releaseRequest.put("sourceType", "external_deployment_inputs");
        releaseRequest.put("sourceVersionRef", "ado://pg123/demo-app-service/releases/" + version);
        releaseRequest.put("deploymentScope", "resource_group");
        releaseRequest.put("parameterDefaults", Map.of(
            "appVersion", version,
            "dataModelVersion", "13"
        ));
        releaseRequest.put("releaseNotes", "ado integration release " + version);
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/releases")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(releaseRequest)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsByteArray()
        ).get("id").asText();
    }

    private String createRun(String releaseId, List<String> targetIds) throws Exception {
        Map<String, Object> runRequest = new LinkedHashMap<>();
        runRequest.put("releaseId", releaseId);
        runRequest.put("targetIds", targetIds);
        runRequest.put("strategyMode", "all_at_once");
        runRequest.put("concurrency", 1);
        MvcResult result = mockMvc.perform(post("/api/v1/runs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(runRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
    }

    private void configureAdoProjectForTest() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("deploymentDriverConfig", Map.of(
            "pipelineSystem", "azure_devops",
            "organization", "https://dev.azure.com/pg123",
            "project", "demo-app-service",
            "pipelineId", "1",
            "branch", "main",
            "azureServiceConnectionName", "mappo-ado-demo-rg-contributor",
            "supportsExternalExecutionHandle", true,
            "supportsExternalLogs", true
        ));
        request.put("accessStrategyConfig", Map.of(
            "authModel", "ado_service_connection",
            "requiresAzureCredential", false,
            "requiresTargetExecutionMetadata", true
        ));
        mockMvc.perform(patch("/api/v1/projects/{projectId}", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions awaitTerminalRun(String runId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andReturn();
            String runStatus = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("status").asText();
            if (!"running".equalsIgnoreCase(runStatus)) {
                return mockMvc.perform(get("/api/v1/runs/{runId}", runId));
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("run did not reach terminal state: " + runId);
    }

    private void registerAdoTarget(
        String targetId,
        String tenantId,
        String subscriptionId,
        Map<String, String> executionConfig
    ) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-" + targetId);
        event.put("eventType", "subscription_purchased");
        event.put("tenantId", tenantId);
        event.put("subscriptionId", subscriptionId);
        event.put("targetId", targetId);
        event.put("displayName", targetId);
        event.put("projectId", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE);
        event.put("customerName", "ADO Demo Customer");
        event.put("tags", Map.of("ring", "canary", "region", "eastus2", "tier", "gold", "environment", "prod"));
        event.put("metadata", Map.of("executionConfig", executionConfig));

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"));
    }

    @TestConfiguration
    static class AzureDevOpsPipelineClientStubConfig {

        @Bean
        @Primary
        AzureDevOpsPipelineClient azureDevOpsPipelineClient() {
            return new StubAzureDevOpsPipelineClient();
        }
    }

    static class StubAzureDevOpsPipelineClient implements AzureDevOpsPipelineClient {

        private final Map<String, AtomicInteger> pollsByRun = new ConcurrentHashMap<>();

        @Override
        public AzureDevOpsPipelineRunRecord queueRun(AzureDevOpsPipelineInputs inputs) {
            String runId = "ado-run-" + inputs.targetId();
            pollsByRun.put(runId, new AtomicInteger(0));
            return new AzureDevOpsPipelineRunRecord(
                runId,
                "stub-" + runId,
                "inProgress",
                "",
                "https://dev.azure.com/pg123/demo-app-service/_build/results?buildId=" + runId + "&view=results",
                "https://dev.azure.com/pg123/demo-app-service/_build/results?buildId=" + runId + "&view=logs",
                "https://dev.azure.com/pg123/demo-app-service/_apis/pipelines/1/runs/" + runId
            );
        }

        @Override
        public AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId) {
            AtomicInteger attempts = pollsByRun.computeIfAbsent(runId, key -> new AtomicInteger(0));
            int poll = attempts.incrementAndGet();
            if (poll < 2) {
                return new AzureDevOpsPipelineRunRecord(
                    runId,
                    "stub-" + runId,
                    "inProgress",
                    "",
                    "https://dev.azure.com/pg123/demo-app-service/_build/results?buildId=" + runId + "&view=results",
                    "https://dev.azure.com/pg123/demo-app-service/_build/results?buildId=" + runId + "&view=logs",
                    "https://dev.azure.com/pg123/demo-app-service/_apis/pipelines/1/runs/" + runId
                );
            }
            return new AzureDevOpsPipelineRunRecord(
                runId,
                "stub-" + runId,
                "completed",
                "succeeded",
                "https://dev.azure.com/pg123/demo-app-service/_build/results?buildId=" + runId + "&view=results",
                "https://dev.azure.com/pg123/demo-app-service/_build/results?buildId=" + runId + "&view=logs",
                "https://dev.azure.com/pg123/demo-app-service/_apis/pipelines/1/runs/" + runId
            );
        }

        @Override
        public List<AzureDevOpsPipelineDefinitionRecord> listPipelines(AzureDevOpsPipelineDiscoveryInputs inputs) {
            return List.of(
                new AzureDevOpsPipelineDefinitionRecord(
                    "1",
                    "Deploy App Service",
                    "\\",
                    "https://dev.azure.com/pg123/demo-app-service/_build?definitionId=1",
                    "https://dev.azure.com/pg123/demo-app-service/_apis/pipelines/1"
                )
            );
        }
    }
}
