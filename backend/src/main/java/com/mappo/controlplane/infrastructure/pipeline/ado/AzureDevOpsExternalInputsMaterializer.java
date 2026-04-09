package com.mappo.controlplane.infrastructure.pipeline.ado;

import com.mappo.controlplane.domain.execution.ReleaseMaterializer;
import com.mappo.controlplane.integrations.azure.access.config.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
class AzureDevOpsExternalInputsMaterializer implements ReleaseMaterializer<AzureDevOpsPipelineInputs> {

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured) {
        return project.deploymentDriver() == ProjectDeploymentDriverType.pipeline_trigger
            && project.releaseArtifactSource() == ProjectReleaseArtifactSourceType.external_deployment_inputs
            && project.accessStrategy() == ProjectAccessStrategyType.azure_workload_rbac
            && project.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig pipelineConfig
            && project.releaseArtifactSourceConfig() instanceof ExternalDeploymentInputsArtifactSourceConfig sourceConfig
            && "azure_devops".equalsIgnoreCase(normalize(pipelineConfig.pipelineSystem()))
            && "azure_devops".equalsIgnoreCase(normalize(sourceConfig.sourceSystem()));
    }

    @Override
    public Class<AzureDevOpsPipelineInputs> materializedType() {
        return AzureDevOpsPipelineInputs.class;
    }

    @Override
    public AzureDevOpsPipelineInputs materialize(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        PipelineTriggerDriverConfig pipelineConfig = (PipelineTriggerDriverConfig) project.deploymentDriverConfig();
        ExternalDeploymentInputsArtifactSourceConfig sourceConfig =
            (ExternalDeploymentInputsArtifactSourceConfig) project.releaseArtifactSourceConfig();
        AzureWorkloadRbacAccessStrategyConfig accessConfig =
            (AzureWorkloadRbacAccessStrategyConfig) project.accessStrategyConfig();

        return new AzureDevOpsPipelineInputs(
            normalize(pipelineConfig.organization()),
            normalize(pipelineConfig.project()),
            normalize(pipelineConfig.pipelineId()),
            normalize(pipelineConfig.branch()),
            normalize(sourceConfig.descriptorPath()),
            normalize(sourceConfig.versionField()),
            normalize(pipelineConfig.azureServiceConnectionName()),
            "",
            target.tenantId() == null ? "" : target.tenantId().toString(),
            target.subscriptionId() == null ? "" : target.subscriptionId().toString(),
            normalize(target.targetId()),
            normalize(release.id()),
            normalize(release.sourceVersion()),
            templateParameters(project, release, target)
        );
    }

    private Map<String, String> templateParameters(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        PipelineTriggerDriverConfig pipelineConfig = (PipelineTriggerDriverConfig) project.deploymentDriverConfig();
        AzureWorkloadRbacAccessStrategyConfig accessConfig =
            (AzureWorkloadRbacAccessStrategyConfig) project.accessStrategyConfig();
        Map<String, String> parameters = new LinkedHashMap<>();
        putAllNormalized(parameters, release.parameterDefaults());
        putAllNormalized(parameters, release.externalInputs());
        putAllNormalized(parameters, target.executionConfig());
        putAllNormalized(parameters, target.tags());

        parameters.put("targetTenantId", target.tenantId() == null ? "" : target.tenantId().toString());
        parameters.put("targetSubscriptionId", target.subscriptionId() == null ? "" : target.subscriptionId().toString());
        parameters.put("targetId", normalize(target.targetId()));
        parameters.put("targetResourceGroup", firstNonBlank(parameters.get("targetResourceGroup"), parameters.get("resourceGroup")));
        parameters.put("targetAppName", firstNonBlank(parameters.get("targetAppName"), parameters.get("appServiceName")));
        parameters.put(
            "appVersion",
            firstNonBlank(
                parameters.get("appVersion"),
                parameters.get("artifactVersion"),
                parameters.get("softwareVersion"),
                release.sourceVersion()
            )
        );
        parameters.put(
            "dataModelVersion",
            firstNonBlank(
                parameters.get("dataModelVersion"),
                value(release.parameterDefaults(), "dataModelVersion"),
                "0"
            )
        );
        parameters.put("deployedBy", firstNonBlank(parameters.get("deployedBy"), "mappo"));

        parameters.put("mappoAccessStrategy", normalize(project.accessStrategy().name()));
        parameters.put("mappoAccessAuthModel", normalize(accessConfig.authModel()));
        parameters.put("mappoAzureServiceConnectionName", normalize(pipelineConfig.azureServiceConnectionName()));
        parameters.put("mappoPipelineSystem", normalize(pipelineConfig.pipelineSystem()));
        parameters.put("mappoProjectId", normalize(project.id()));
        parameters.put("mappoTargetId", normalize(target.targetId()));
        parameters.put("mappoReleaseId", normalize(release.id()));
        parameters.put("mappoReleaseVersion", normalize(release.sourceVersion()));
        parameters.put("mappoTargetTenantId", target.tenantId() == null ? "" : target.tenantId().toString());
        parameters.put("mappoTargetSubscriptionId", target.subscriptionId() == null ? "" : target.subscriptionId().toString());
        return parameters;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private void putAllNormalized(Map<String, String> target, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        values.forEach((key, value) -> {
            String normalizedKey = normalize(key);
            if (!normalizedKey.isBlank()) {
                target.put(normalizedKey, normalize(value));
            }
        });
    }

    private String value(Map<String, String> values, String key) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return normalize(values.get(key));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
