package com.mappo.controlplane.integrations.azure.templatespec;

import com.mappo.controlplane.domain.execution.TargetDeploymentOutcome;

import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.execution.DeploymentDriver;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface TemplateSpecExecutor extends DeploymentDriver {

    @Override
    default boolean supports(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return runtimeConfigured
            && project.deploymentDriver() == ProjectDeploymentDriverType.azure_template_spec
            && release.deploymentScope() == MappoDeploymentScope.resource_group;
    }

    @Override
    default String verificationMessage(ProjectDefinition project, ReleaseRecord release) {
        return "Verification passed: ARM deployment completed successfully.";
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
