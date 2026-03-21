package com.mappo.controlplane;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineClient;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDefinitionRecord;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDiscoveryInputs;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineInputs;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineRunRecord;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsServiceConnectionDefinitionRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@SpringBootTest
class ProjectConfigurationApiIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createAndPatchProjectProduceAuditEvents() throws Exception {
        String projectId = "project-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("id", projectId);
        createRequest.put("name", "Custom ADO Project");
        createRequest.put("accessStrategy", "azure_workload_rbac");
        createRequest.put("deploymentDriver", "pipeline_trigger");
        createRequest.put("releaseArtifactSource", "external_deployment_inputs");
        createRequest.put("runtimeHealthProvider", "http_endpoint");

        mockMvc.perform(post("/api/v1/projects")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(projectId))
            .andExpect(jsonPath("$.deploymentDriver").value("pipeline_trigger"));

        Map<String, Object> patchRequest = new LinkedHashMap<>();
        patchRequest.put("name", "Custom ADO Project Updated");
        patchRequest.put("deploymentDriverConfig", Map.of(
            "organization", "https://dev.azure.com/contoso",
            "project", "demo-app-service",
            "pipelineId", "123",
            "azureServiceConnectionName", "contoso-rg-contributor"
        ));

        mockMvc.perform(patch("/api/v1/projects/{projectId}", projectId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(patchRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Custom ADO Project Updated"))
            .andExpect(jsonPath("$.deploymentDriverConfig.pipelineId").value("123"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/audit", projectId)
                .queryParam("page", "0")
                .queryParam("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalItems").value(2))
            .andExpect(jsonPath("$.items[0].action").value("updated"))
            .andExpect(jsonPath("$.items[1].action").value("created"));
    }

    @Test
    void validateAdoProjectDetectsMissingPatAndWebhookSecretWhenConfigExists() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/validate", "azure-appservice-ado-pipeline")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "scopes": ["credentials", "webhook"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.findings[*].code", hasItems(
                "AZURE_DEVOPS_PAT_MISSING",
                "AZURE_DEVOPS_WEBHOOK_SECRET_MISSING"
            )));
    }

    @Test
    void validateTargetContractWarnsWhenProjectHasNoTargets() throws Exception {
        String projectId = "project-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("id", projectId);
        createRequest.put("name", "No Targets Yet");
        createRequest.put("accessStrategy", "azure_workload_rbac");
        createRequest.put("deploymentDriver", "pipeline_trigger");
        createRequest.put("releaseArtifactSource", "external_deployment_inputs");
        createRequest.put("runtimeHealthProvider", "http_endpoint");

        mockMvc.perform(post("/api/v1/projects")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createRequest)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects/{projectId}/validate", projectId)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "scopes": ["target_contract"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.findings[*].code", hasItem("NO_TARGETS_AVAILABLE")));
    }

    @Test
    void discoverAdoPipelinesReturnsConfiguredPipelines() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-driver/ado/pipelines/discover", "azure-appservice-ado-pipeline")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "organization": "https://dev.azure.com/pg123",
                      "project": "demo-app-service",
                      "personalAccessTokenRef": "literal:test"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value("azure-appservice-ado-pipeline"))
            .andExpect(jsonPath("$.organization").value("https://dev.azure.com/pg123"))
            .andExpect(jsonPath("$.project").value("demo-app-service"))
            .andExpect(jsonPath("$.pipelines[0].id").value("1"))
            .andExpect(jsonPath("$.pipelines[0].name").value("Deploy App Service"));
    }

    @Test
    void discoverAdoServiceConnectionsReturnsConfiguredConnections() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-driver/ado/service-connections/discover", "azure-appservice-ado-pipeline")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "organization": "https://dev.azure.com/pg123",
                      "project": "demo-app-service",
                      "personalAccessTokenRef": "literal:test"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value("azure-appservice-ado-pipeline"))
            .andExpect(jsonPath("$.organization").value("https://dev.azure.com/pg123"))
            .andExpect(jsonPath("$.project").value("demo-app-service"))
            .andExpect(jsonPath("$.serviceConnections[0].id").value("svc-1"))
            .andExpect(jsonPath("$.serviceConnections[0].name").value("mappo-ado-demo-rg-contributor"));
    }

    @TestConfiguration
    static class AzureDevOpsPipelineClientStubConfig {

        @Bean
        @Primary
        AzureDevOpsPipelineClient azureDevOpsPipelineClient() {
            return new AzureDevOpsPipelineClient() {
                @Override
                public AzureDevOpsPipelineRunRecord queueRun(AzureDevOpsPipelineInputs inputs) {
                    throw new UnsupportedOperationException("not used");
                }

                @Override
                public AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId) {
                    throw new UnsupportedOperationException("not used");
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
                        ),
                        new AzureDevOpsPipelineDefinitionRecord(
                            "2",
                            "Smoke Test",
                            "\\",
                            "https://dev.azure.com/pg123/demo-app-service/_build?definitionId=2",
                            "https://dev.azure.com/pg123/demo-app-service/_apis/pipelines/2"
                        )
                    );
                }

                @Override
                public List<AzureDevOpsServiceConnectionDefinitionRecord> listServiceConnections(
                    AzureDevOpsPipelineDiscoveryInputs inputs
                ) {
                    return List.of(
                        new AzureDevOpsServiceConnectionDefinitionRecord(
                            "svc-1",
                            "mappo-ado-demo-rg-contributor",
                            "azurerm",
                            "https://dev.azure.com/pg123/demo-app-service/_settings/adminservices?resourceId=svc-1"
                        ),
                        new AzureDevOpsServiceConnectionDefinitionRecord(
                            "svc-2",
                            "mappo-ado-rg-readonly",
                            "azurerm",
                            "https://dev.azure.com/pg123/demo-app-service/_settings/adminservices?resourceId=svc-2"
                        )
                    );
                }
            };
        }
    }
}
