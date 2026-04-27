package com.mappo.controlplane;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsPipelineClient;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsBranchDefinitionRecord;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsBranchDiscoveryInputs;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsPipelineDefinitionRecord;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsPipelineDiscoveryInputs;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsPipelineInputs;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsProjectDefinitionRecord;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsPipelineRunRecord;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsRepositoryDefinitionRecord;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
    "mappo.azure-devops.personal-access-token=test-pat"
})
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
        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("name", "Custom ADO Project");
        createRequest.put("themeKey", "scalr-slate");
        createRequest.put("accessStrategy", "azure_workload_rbac");
        createRequest.put("deploymentDriver", "pipeline_trigger");
        createRequest.put("releaseArtifactSource", "external_deployment_inputs");
        createRequest.put("runtimeHealthProvider", "http_endpoint");

        MvcResult createResult = mockMvc.perform(post("/api/v1/projects")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deploymentDriver").value("pipeline_trigger"))
            .andExpect(jsonPath("$.themeKey").value("scalr-slate"))
            .andReturn();
        String projectId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray()).get("id").asText();

        Map<String, Object> patchRequest = new LinkedHashMap<>();
        patchRequest.put("name", "Custom ADO Project Updated");
        patchRequest.put("themeKey", "vectr-signal");
        patchRequest.put("deploymentDriverConfig", Map.of(
            "organization", "https://dev.azure.com/contoso",
            "project", "sample-app-service",
            "pipelineId", "123"
        ));

        mockMvc.perform(patch("/api/v1/projects/{projectId}", projectId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(patchRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Custom ADO Project Updated"))
            .andExpect(jsonPath("$.themeKey").value("vectr-signal"))
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
    void validateAdoProjectDetectsMissingWebhookSecretWhenConfigExists() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/validate", "azure-appservice-ado-pipeline")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "scopes": ["credentials", "webhook"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.findings[*].code", hasItem("AZURE_DEVOPS_WEBHOOK_SECRET_MISSING")));
    }

    @Test
    void validateTargetContractWarnsWhenProjectHasNoTargets() throws Exception {
        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("name", "No Targets Yet");
        createRequest.put("accessStrategy", "azure_workload_rbac");
        createRequest.put("deploymentDriver", "pipeline_trigger");
        createRequest.put("releaseArtifactSource", "external_deployment_inputs");
        createRequest.put("runtimeHealthProvider", "http_endpoint");

        MvcResult createResult = mockMvc.perform(post("/api/v1/projects")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        String projectId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray()).get("id").asText();

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
    void deleteProjectRemovesProjectScopedTargetsReleasesAndEvents() throws Exception {
        String targetId = "target-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("name", "Delete Me");
        createRequest.put("accessStrategy", "azure_workload_rbac");
        createRequest.put("deploymentDriver", "azure_deployment_stack");
        createRequest.put("releaseArtifactSource", "blob_arm_template");
        createRequest.put("runtimeHealthProvider", "http_endpoint");

        MvcResult createResult = mockMvc.perform(post("/api/v1/projects")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        String projectId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray()).get("id").asText();

        Map<String, Object> onboardingEvent = new LinkedHashMap<>();
        onboardingEvent.put("eventId", "evt-" + targetId);
        onboardingEvent.put("eventType", "subscription_purchased");
        onboardingEvent.put("projectId", projectId);
        onboardingEvent.put("tenantId", "11111111-1111-1111-1111-111111111111");
        onboardingEvent.put("subscriptionId", "22222222-2222-2222-2222-222222222222");
        onboardingEvent.put("targetId", targetId);
        onboardingEvent.put("displayName", "Delete Target");
        onboardingEvent.put(
            "containerAppResourceId",
            "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-delete/providers/Microsoft.App/containerApps/ca-delete"
        );
        onboardingEvent.put("managedResourceGroupId", "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-delete");
        onboardingEvent.put("containerAppName", "ca-delete");
        onboardingEvent.put("customerName", "Delete Customer");
        onboardingEvent.put("tags", Map.of("ring", "prod", "region", "eastus", "tier", "gold", "environment", "prod"));
        onboardingEvent.put("metadata", Map.of("source", "marketplace-forwarder", "marketplacePayloadId", "mp-delete-001"));

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(onboardingEvent)))
            .andExpect(status().isOk());

        Map<String, Object> releaseCreateRequest = new LinkedHashMap<>();
        releaseCreateRequest.put("projectId", projectId);
        releaseCreateRequest.put("sourceRef", "github://example/delete-app/mainTemplate.json");
        releaseCreateRequest.put("sourceVersion", "2026.04.04.99");
        releaseCreateRequest.put("sourceType", "deployment_stack");
        releaseCreateRequest.put("sourceVersionRef", "https://example.invalid/releases/2026.04.04.99/mainTemplate.json");
        releaseCreateRequest.put("parameterDefaults", Map.of("softwareVersion", "2026.04.04.99"));

        mockMvc.perform(post("/api/v1/releases")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(releaseCreateRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.projectId").value(projectId));

        mockMvc.perform(delete("/api/v1/projects/{projectId}", projectId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(hasItem(projectId))));

        mockMvc.perform(get("/api/v1/targets/page")
                .queryParam("projectId", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/v1/releases")
                .queryParam("projectId", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/onboarding/events")
                .queryParam("projectId", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void discoverAdoPipelinesReturnsConfiguredPipelines() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-driver/ado/pipelines/discover", "azure-appservice-ado-pipeline")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "organization": "https://dev.azure.com/example-ado-org",
                      "project": "sample-app-service",
                      "providerConnectionId": "%s"
                    }
                    """.formatted(adoProviderConnectionId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value("azure-appservice-ado-pipeline"))
            .andExpect(jsonPath("$.organization").value("https://dev.azure.com/example-ado-org"))
            .andExpect(jsonPath("$.project").value("sample-app-service"))
            .andExpect(jsonPath("$.pipelines[0].id").value("1"))
            .andExpect(jsonPath("$.pipelines[0].name").value("Deploy App Service"));
    }

    @Test
    void discoverAdoRepositoriesReturnsConfiguredRepositories() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-driver/ado/repositories/discover", "azure-appservice-ado-pipeline")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "organization": "https://dev.azure.com/example-ado-org",
                      "project": "sample-app-service",
                      "providerConnectionId": "%s"
                    }
                    """.formatted(adoProviderConnectionId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value("azure-appservice-ado-pipeline"))
            .andExpect(jsonPath("$.organization").value("https://dev.azure.com/example-ado-org"))
            .andExpect(jsonPath("$.project").value("sample-app-service"))
            .andExpect(jsonPath("$.repositories[0].id").value("repo-1"))
            .andExpect(jsonPath("$.repositories[0].name").value("sample-app-service"));
    }

    @Test
    void discoverAdoBranchesReturnsConfiguredBranches() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-driver/ado/branches/discover", "azure-appservice-ado-pipeline")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "organization": "https://dev.azure.com/example-ado-org",
                      "project": "sample-app-service",
                      "providerConnectionId": "%s",
                      "repositoryId": "repo-1",
                      "repository": "sample-app-service"
                    }
                    """.formatted(adoProviderConnectionId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value("azure-appservice-ado-pipeline"))
            .andExpect(jsonPath("$.organization").value("https://dev.azure.com/example-ado-org"))
            .andExpect(jsonPath("$.project").value("sample-app-service"))
            .andExpect(jsonPath("$.repositoryId").value("repo-1"))
            .andExpect(jsonPath("$.repository").value("sample-app-service"))
            .andExpect(jsonPath("$.branches[0].name").value("main"))
            .andExpect(jsonPath("$.branches[0].refName").value("refs/heads/main"));
    }

    @Test
    void patchProviderConnectionPersistsPersonalAccessTokenRef() throws Exception {
        String connectionId = adoProviderConnectionId();

        mockMvc.perform(patch("/api/v1/provider-connections/{connectionId}", connectionId)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "organizationUrl": "https://example-ado-org.visualstudio.com/sample-app-service/_git/sample-app-service",
                      "personalAccessTokenRef": "mappo.azure-devops.personal-access-token"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(connectionId))
            .andExpect(jsonPath("$.organizationUrl").value("https://example-ado-org.visualstudio.com"))
            .andExpect(jsonPath("$.personalAccessTokenRef").value("mappo.azure-devops.personal-access-token"));
    }

    @Test
    void verifyProviderConnectionEnumeratesReachableProjects() throws Exception {
        mockMvc.perform(post("/api/v1/provider-connections/ado/verify")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "organizationUrl": "https://example-ado-org.visualstudio.com/sample-app-service/_git/sample-app-service",
                      "personalAccessTokenRef": "mappo.azure-devops.personal-access-token"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizationUrl").value("https://example-ado-org.visualstudio.com"))
            .andExpect(jsonPath("$.projects[0].name").value("sample-app-service"));
    }

    @TestConfiguration
    static class AzureDevOpsPipelineClientStubConfig {

        @Bean
        @Primary
        AzureDevOpsPipelineClient azureDevOpsPipelineClient() {
            return new AzureDevOpsPipelineClient() {
                @Override
                public List<AzureDevOpsProjectDefinitionRecord> listProjects(
                    String organization,
                    String personalAccessToken
                ) {
                    return List.of(
                        new AzureDevOpsProjectDefinitionRecord(
                            "sample-app-service",
                            "sample-app-service",
                            organization + "/sample-app-service"
                        ),
                        new AzureDevOpsProjectDefinitionRecord(
                            "shared-platform",
                            "shared-platform",
                            organization + "/shared-platform"
                        )
                    );
                }

                @Override
                public AzureDevOpsPipelineRunRecord queueRun(AzureDevOpsPipelineInputs inputs) {
                    throw new UnsupportedOperationException("not used");
                }

                @Override
                public AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId) {
                    throw new UnsupportedOperationException("not used");
                }

                @Override
                public List<AzureDevOpsBranchDefinitionRecord> listBranches(
                    AzureDevOpsBranchDiscoveryInputs inputs
                ) {
                    return List.of(
                        new AzureDevOpsBranchDefinitionRecord("main", "refs/heads/main"),
                        new AzureDevOpsBranchDefinitionRecord("release", "refs/heads/release")
                    );
                }

                @Override
                public List<AzureDevOpsRepositoryDefinitionRecord> listRepositories(
                    AzureDevOpsPipelineDiscoveryInputs inputs
                ) {
                    return List.of(
                        new AzureDevOpsRepositoryDefinitionRecord(
                            "repo-1",
                            "sample-app-service",
                            "refs/heads/main",
                            "https://dev.azure.com/example-ado-org/sample-app-service/_git/sample-app-service",
                            "https://example-ado-org@dev.azure.com/example-ado-org/sample-app-service/_git/sample-app-service"
                        ),
                        new AzureDevOpsRepositoryDefinitionRecord(
                            "repo-2",
                            "shared-library",
                            "refs/heads/main",
                            "https://dev.azure.com/example-ado-org/sample-app-service/_git/shared-library",
                            "https://example-ado-org@dev.azure.com/example-ado-org/sample-app-service/_git/shared-library"
                        )
                    );
                }

                @Override
                public List<AzureDevOpsPipelineDefinitionRecord> listPipelines(AzureDevOpsPipelineDiscoveryInputs inputs) {
                    return List.of(
                        new AzureDevOpsPipelineDefinitionRecord(
                            "1",
                            "Deploy App Service",
                            "\\",
                            "https://dev.azure.com/example-ado-org/sample-app-service/_build?definitionId=1",
                            "https://dev.azure.com/example-ado-org/sample-app-service/_apis/pipelines/1"
                        ),
                        new AzureDevOpsPipelineDefinitionRecord(
                            "2",
                            "Smoke Test",
                            "\\",
                            "https://dev.azure.com/example-ado-org/sample-app-service/_build?definitionId=2",
                            "https://dev.azure.com/example-ado-org/sample-app-service/_apis/pipelines/2"
                        )
                    );
                }

            };
        }
    }
}
