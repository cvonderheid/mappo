package com.mappo.controlplane.domain.execution;

import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface DeploymentDriver {

    boolean supports(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured);

    TargetDeploymentOutcome deploy(
        String runId,
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        ResolvedTargetAccessContext accessContext
    );

    default String verificationMessage(ProjectDefinition project, ReleaseRecord release) {
        return "Verification passed: deployment completed successfully.";
    }
}
