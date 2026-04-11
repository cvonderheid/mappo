package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.mappo.controlplane.domain.execution.TargetPreviewOutcome;

import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.execution.DeploymentPreviewDriver;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunPreviewMode;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface DeploymentStackPreviewExecutor extends DeploymentPreviewDriver {

    @Override
    default boolean supports(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return runtimeConfigured
            && project.deploymentDriver() == ProjectDeploymentDriverType.azure_deployment_stack
            && release.deploymentScope() == MappoDeploymentScope.resource_group;
    }

    @Override
    default RunPreviewMode mode() {
        return RunPreviewMode.ARM_WHAT_IF;
    }

    @Override
    TargetPreviewOutcome preview(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        ResolvedTargetAccessContext accessContext
    );
}
