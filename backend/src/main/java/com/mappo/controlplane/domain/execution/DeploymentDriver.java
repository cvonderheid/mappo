package com.mappo.controlplane.domain.execution;

import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.TargetDeploymentOutcome;

public interface DeploymentDriver {

    boolean supports(ReleaseRecord release, boolean azureConfigured);

    TargetDeploymentOutcome deploy(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    );
}
