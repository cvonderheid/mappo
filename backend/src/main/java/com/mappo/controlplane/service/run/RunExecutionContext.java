package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import java.util.List;
import java.util.Map;

public record RunExecutionContext(
    String runId,
    RunDetailRecord run,
    ReleaseRecord release,
    ProjectExecutionCapabilities capabilities,
    List<String> queuedTargetIds,
    List<TargetRecord> executableTargets,
    List<String> missingTargetIds,
    Map<String, TargetExecutionContextRecord> executionContextsByTarget
) {
}
