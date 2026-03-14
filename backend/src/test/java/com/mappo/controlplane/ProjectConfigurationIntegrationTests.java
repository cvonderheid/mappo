package com.mappo.controlplane;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ProjectConfigurationIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void patchProjectConfigurationUpdatesAdoProjectWithoutHardcodedDefaults() throws Exception {
        Map<String, Object> original = currentProjectPatchPayload(BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Azure App Service ADO Production");
        request.put("accessStrategyConfig", Map.of(
            "authModel", "ado_service_connection",
            "requiresAzureCredential", false,
            "requiresTargetExecutionMetadata", true
        ));
        request.put("deploymentDriverConfig", Map.of(
            "pipelineSystem", "azure_devops",
            "organization", "https://dev.azure.com/contoso",
            "project", "customer-app-service",
            "pipelineId", "42",
            "branch", "release",
            "azureServiceConnectionName", "contoso-rg-contributor"
        ));
        request.put("runtimeHealthProviderConfig", Map.of(
            "path", "/healthz",
            "expectedStatus", 200,
            "timeoutMs", 7000
        ));

        try {
            mockMvc.perform(patch("/api/v1/projects/{projectId}", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE))
                .andExpect(jsonPath("$.name").value("Azure App Service ADO Production"))
                .andExpect(jsonPath("$.deploymentDriverConfig.organization").value("https://dev.azure.com/contoso"))
                .andExpect(jsonPath("$.deploymentDriverConfig.project").value("customer-app-service"))
                .andExpect(jsonPath("$.deploymentDriverConfig.pipelineId").value("42"))
                .andExpect(jsonPath("$.deploymentDriverConfig.azureServiceConnectionName").value("contoso-rg-contributor"))
                .andExpect(jsonPath("$.runtimeHealthProviderConfig.path").value("/healthz"));
        } finally {
            mockMvc.perform(patch("/api/v1/projects/{projectId}", BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(original)))
                .andExpect(status().isOk());
        }
    }

    private Map<String, Object> currentProjectPatchPayload(String projectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        for (JsonNode item : root) {
            if (projectId.equals(item.path("id").asText())) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", item.path("name").asText());
                payload.put("accessStrategyConfig", objectMapper.convertValue(item.path("accessStrategyConfig"), Map.class));
                payload.put("deploymentDriverConfig", objectMapper.convertValue(item.path("deploymentDriverConfig"), Map.class));
                payload.put("releaseArtifactSourceConfig", objectMapper.convertValue(item.path("releaseArtifactSourceConfig"), Map.class));
                payload.put("runtimeHealthProviderConfig", objectMapper.convertValue(item.path("runtimeHealthProviderConfig"), Map.class));
                return payload;
            }
        }
        throw new AssertionError("project not found in list: " + projectId);
    }
}
