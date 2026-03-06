package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface TemplateSpecExecutor {

    TargetDeploymentOutcome deploy(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    );
}
