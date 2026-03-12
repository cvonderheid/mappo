package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.execution.DeploymentDriver;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface DeploymentStackExecutor extends DeploymentDriver {

    @Override
    default boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured) {
        return azureConfigured
            && project.deploymentDriver() == ProjectDeploymentDriverType.azure_deployment_stack
            && release.deploymentScope() == MappoDeploymentScope.resource_group;
    }

    @Override
    TargetDeploymentOutcome deploy(
        String runId,
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        ResolvedTargetAccessContext accessContext
    );
}
