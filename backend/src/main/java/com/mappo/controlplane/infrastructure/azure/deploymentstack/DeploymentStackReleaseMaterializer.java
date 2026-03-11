package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.mappo.controlplane.domain.execution.ReleaseMaterializer;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DeploymentStackReleaseMaterializer implements ReleaseMaterializer<DeploymentStackTemplateInputs> {

    private final DeploymentStackCurrentContainerAppLoader currentContainerAppLoader;
    private final DeploymentStackParameterFactory parameterFactory;
    private final ReleaseArtifactTemplateLoader releaseArtifactTemplateLoader;

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured) {
        return project.deploymentDriver() == ProjectDeploymentDriverType.azure_deployment_stack
            && project.releaseArtifactSource() == ProjectReleaseArtifactSourceType.blob_arm_template;
    }

    @Override
    public Class<DeploymentStackTemplateInputs> materializedType() {
        return DeploymentStackTemplateInputs.class;
    }

    @Override
    public DeploymentStackTemplateInputs materialize(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        var currentContainerApp = currentContainerAppLoader.load(target);
        return new DeploymentStackTemplateInputs(
            normalize(target.managedResourceGroupId()),
            releaseArtifactTemplateLoader.loadTemplateDefinition(release),
            parameterFactory.deploymentParameters(release.parameterDefaults(), target, currentContainerApp)
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
