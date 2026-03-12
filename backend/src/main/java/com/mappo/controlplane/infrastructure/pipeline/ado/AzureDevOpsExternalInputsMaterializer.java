package com.mappo.controlplane.infrastructure.pipeline.ado;

import com.mappo.controlplane.domain.execution.ReleaseMaterializer;
import com.mappo.controlplane.domain.project.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.domain.project.LighthouseDelegatedAccessStrategyConfig;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
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
            && project.accessStrategy() == ProjectAccessStrategyType.lighthouse_delegated_access
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
        LighthouseDelegatedAccessStrategyConfig accessConfig =
            (LighthouseDelegatedAccessStrategyConfig) project.accessStrategyConfig();

        return new AzureDevOpsPipelineInputs(
            normalize(pipelineConfig.organization()),
            normalize(pipelineConfig.project()),
            normalize(pipelineConfig.pipelineId()),
            normalize(pipelineConfig.branch()),
            normalize(sourceConfig.descriptorPath()),
            normalize(sourceConfig.versionField()),
            normalize(accessConfig.azureServiceConnectionName()),
            normalize(accessConfig.managingTenantId()),
            normalize(accessConfig.managingPrincipalClientId()),
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
        Map<String, String> parameters = new LinkedHashMap<>();
        putAllNormalized(parameters, release.externalInputs());
        putAllNormalized(parameters, target.executionConfig());
        putAllNormalized(parameters, target.tags());

        parameters.put("mappoProjectId", normalize(project.id()));
        parameters.put("mappoTargetId", normalize(target.targetId()));
        parameters.put("mappoReleaseId", normalize(release.id()));
        parameters.put("mappoReleaseVersion", normalize(release.sourceVersion()));
        parameters.put("mappoTargetTenantId", target.tenantId() == null ? "" : target.tenantId().toString());
        parameters.put("mappoTargetSubscriptionId", target.subscriptionId() == null ? "" : target.subscriptionId().toString());
        return parameters;
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

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
