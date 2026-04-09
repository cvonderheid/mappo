package com.mappo.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.persistence.target.TargetExecutionConfigCommandRepository;
import com.mappo.controlplane.persistence.target.TargetExecutionContextRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class TargetExecutionContextRepositoryIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TargetExecutionContextRepository targetExecutionContextRepository;

    @Autowired
    private TargetExecutionConfigCommandRepository targetExecutionConfigCommandRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void executionContextIncludesNormalizedExecutionConfigEntries() throws Exception {
        registerTarget("target-config-01", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        targetExecutionConfigCommandRepository.replaceConfigEntries(
            "target-config-01",
            Map.of(
                "pipeline.organization", "contoso",
                "pipeline.project", "platform",
                "pipeline.id", "42"
            )
        );

        TargetExecutionContextRecord context = targetExecutionContextRepository
            .getExecutionContextsByIds(java.util.List.of("target-config-01"))
            .getFirst();

        assertThat(context.executionConfig())
            .containsEntry("pipeline.organization", "contoso")
            .containsEntry("pipeline.project", "platform")
            .containsEntry("pipeline.id", "42");
    }

    @Test
    void adoProjectOnboardingCanRegisterTargetWithoutContainerAppResourceId() throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-ado-target-01");
        event.put("eventType", "subscription_purchased");
        event.put("tenantId", "5476530d-fba1-4cd5-b2c0-fa118c5ff36e");
        event.put("subscriptionId", "597f46c7-2ce0-440e-962d-453e486f159d");
        event.put("targetId", "ado-target-01");
        event.put("displayName", "ADO Target 01");
        event.put("projectId", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE);
        event.put("customerName", "ADO Customer");
        event.put("tags", Map.of("ring", "prod", "region", "eastus", "tier", "gold", "environment", "prod"));
        event.put(
            "metadata",
            Map.of(
                "source", "service-hook",
                "executionConfig", Map.of(
                    "pipeline.organization", "contoso",
                    "pipeline.project", "platform",
                    "pipeline.id", "42",
                    "resourceGroupName", "rg-ado-target-01",
                    "appServiceName", "app-ado-target-01"
                )
            )
        );

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"));

        TargetExecutionContextRecord context = targetExecutionContextRepository
            .getExecutionContextsByIds(java.util.List.of("ado-target-01"))
            .getFirst();

        assertThat(context.containerAppResourceId()).isNull();
        assertThat(context.executionConfig())
            .containsEntry("pipeline.organization", "contoso")
            .containsEntry("pipeline.project", "platform")
            .containsEntry("pipeline.id", "42")
            .containsEntry("resourceGroupName", "rg-ado-target-01")
            .containsEntry("appServiceName", "app-ado-target-01");
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
}
