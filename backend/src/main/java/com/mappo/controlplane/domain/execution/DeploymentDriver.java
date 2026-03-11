package com.mappo.controlplane.domain.execution;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.TargetDeploymentOutcome;

public interface DeploymentDriver {

    boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured);

    TargetDeploymentOutcome deploy(
        String runId,
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    );
}
