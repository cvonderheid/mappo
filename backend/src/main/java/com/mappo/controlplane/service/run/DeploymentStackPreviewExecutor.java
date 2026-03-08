package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface DeploymentStackPreviewExecutor {

    TargetPreviewOutcome preview(ReleaseRecord release, TargetExecutionContextRecord target);
}
