package com.mappo.controlplane.infrastructure.pipeline.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.mappo.controlplane.domain.project.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.domain.project.HttpEndpointRuntimeHealthProviderConfig;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.model.ReleaseExecutionSettingsRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AzureDevOpsExternalInputsMaterializerTests {

    private final AzureDevOpsExternalInputsMaterializer materializer = new AzureDevOpsExternalInputsMaterializer();

    @Test
    void materializeBuildsCanonicalPipelineTemplateParameters() {
        ProjectDefinition project = new ProjectDefinition(
            "azure-appservice-ado-pipeline",
            "Azure App Service ADO Pipeline",
            null,
            null,
            ProjectAccessStrategyType.azure_workload_rbac,
            new AzureWorkloadRbacAccessStrategyConfig("ado_service_connection", false, true),
            ProjectDeploymentDriverType.pipeline_trigger,
            new PipelineTriggerDriverConfig(
                "azure_devops",
                "https://dev.azure.com/pg123",
                "demo-app-service",
                "1",
                "main",
                "mappo-ado-demo-rg-contributor",
                true,
                true
            ),
            ProjectReleaseArtifactSourceType.external_deployment_inputs,
            new ExternalDeploymentInputsArtifactSourceConfig("azure_devops", "pipelineInputs", "artifactVersion"),
            ProjectRuntimeHealthProviderType.http_endpoint,
            HttpEndpointRuntimeHealthProviderConfig.defaults()
        );

        ReleaseRecord release = new ReleaseRecord(
            "rel-ado-001",
            "azure-appservice-ado-pipeline",
            "ado://pg123/demo-app-service/pipeline/1",
            "2026.03.13.1",
            MappoReleaseSourceType.external_deployment_inputs,
            "ado://pg123/demo-app-service/releases/2026.03.13.1",
            MappoDeploymentScope.resource_group,
            new ReleaseExecutionSettingsRecord(MappoArmDeploymentMode.incremental, false, true),
            Map.of("softwareVersion", "2026.03.13.1", "dataModelVersion", "13"),
            Map.of("artifactVersion", "2026.03.13.1", "deployedBy", "ado"),
            "test",
            java.util.List.of(),
            OffsetDateTime.now()
        );

        TargetExecutionContextRecord target = new TargetExecutionContextRecord(
            "ado-target-01",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "",
            "",
            "",
            MappoRegistryAuthMode.none,
            "",
            "",
            "",
            Map.of("ring", "canary"),
            MappoSimulatedFailureMode.none,
            Map.of("resourceGroup", "rg-ado-target-01", "appServiceName", "app-ado-target-01")
        );

        AzureDevOpsPipelineInputs inputs = materializer.materialize(project, release, target);

        assertThat(inputs.organization()).isEqualTo("https://dev.azure.com/pg123");
        assertThat(inputs.project()).isEqualTo("demo-app-service");
        assertThat(inputs.pipelineId()).isEqualTo("1");
        assertThat(inputs.azureServiceConnectionName()).isEqualTo("mappo-ado-demo-rg-contributor");
        assertThat(inputs.personalAccessToken()).isEqualTo("");
        assertThat(inputs.templateParameters()).containsEntry("targetSubscriptionId", "11111111-1111-1111-1111-111111111111");
        assertThat(inputs.templateParameters()).containsEntry("targetResourceGroup", "rg-ado-target-01");
        assertThat(inputs.templateParameters()).containsEntry("targetAppName", "app-ado-target-01");
        assertThat(inputs.templateParameters()).containsEntry("targetId", "ado-target-01");
        assertThat(inputs.templateParameters()).containsEntry("appVersion", "2026.03.13.1");
        assertThat(inputs.templateParameters()).containsEntry("dataModelVersion", "13");
        assertThat(inputs.templateParameters()).containsEntry("deployedBy", "ado");
    }
}
