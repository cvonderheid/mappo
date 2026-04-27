package com.mappo.controlplane;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunPreviewChangeRecord;
import com.mappo.controlplane.model.RunPreviewPropertyChangeRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.integrations.azure.deploymentstack.DeploymentStackPreviewExecutor;
import com.mappo.controlplane.domain.execution.TargetPreviewOutcome;
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
})
class RunPreviewIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void previewRunUsesDeploymentStackWhatIfExecutorWhenAzureConfigIsPresent() throws Exception {
        registerTarget("target-preview-01", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        Map<String, Object> releaseRequest = new LinkedHashMap<>();
        releaseRequest.put("projectId", BuiltinProjects.AZURE_MANAGED_APP_DEPLOYMENT_STACK);
        releaseRequest.put("sourceRef", "github://example-org/mappo-release-catalog/releases/releases.manifest.json");
        releaseRequest.put("sourceVersion", "2026.03.08.2");
        releaseRequest.put("sourceType", "deployment_stack");
        releaseRequest.put("sourceVersionRef", "https://storage.example.com/releases/2026.03.08.2/mainTemplate.json");
        releaseRequest.put("deploymentScope", "resource_group");
        releaseRequest.put("releaseNotes", "preview execution test");

        String releaseId = objectMapper.readTree(mockMvc.perform(post("/api/v1/releases")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(releaseRequest)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsByteArray()
        ).get("id").asText();

        Map<String, Object> previewRequest = new LinkedHashMap<>();
        previewRequest.put("releaseId", releaseId);
        previewRequest.put("targetIds", List.of("target-preview-01"));
        previewRequest.put("strategyMode", "all_at_once");
        previewRequest.put("concurrency", 1);

        mockMvc.perform(post("/api/v1/runs/preview")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(previewRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("ARM_WHAT_IF"))
            .andExpect(jsonPath("$.caveat").value(containsString("Deployment Stack what-if is not natively available")))
            .andExpect(jsonPath("$.targets[0].status").value("PREVIEWED"))
            .andExpect(jsonPath("$.targets[0].summary").value("ARM what-if found 2 resource changes (1 Create, 1 Modify)."))
            .andExpect(jsonPath("$.targets[0].changes[0].resourceId").value("/subscriptions/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/resourceGroups/rg-target-preview-01/providers/Microsoft.App/containerApps/ca-target-preview-01"))
            .andExpect(jsonPath("$.targets[0].changes[0].propertyChanges[0].path").value("$.properties.template.containers[0].image"));
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
    static class DeploymentStackPreviewExecutorStubConfig {

        @Bean
        @Primary
        DeploymentStackPreviewExecutor deploymentStackPreviewExecutor() {
            return new StubDeploymentStackPreviewExecutor();
        }
    }

    static class StubDeploymentStackPreviewExecutor implements DeploymentStackPreviewExecutor {

        @Override
        public TargetPreviewOutcome preview(
            ProjectDefinition project,
            ReleaseRecord release,
            TargetExecutionContextRecord target,
            ResolvedTargetAccessContext accessContext
        ) {
            return new TargetPreviewOutcome(
                "ARM what-if found 2 resource changes (1 Create, 1 Modify).",
                List.of(),
                List.of(
                    new RunPreviewChangeRecord(
                        "/subscriptions/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/resourceGroups/rg-target-preview-01/providers/Microsoft.App/containerApps/ca-target-preview-01",
                        "Create",
                        null,
                        List.of(
                            new RunPreviewPropertyChangeRecord("$.properties.template.containers[0].image", "Create")
                        )
                    ),
                    new RunPreviewChangeRecord(
                        "/subscriptions/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/resourceGroups/rg-target-preview-01/providers/Microsoft.App/managedEnvironments/cae-target-preview-01",
                        "Modify",
                        null,
                        List.of()
                    )
                )
            );
        }
    }
}
