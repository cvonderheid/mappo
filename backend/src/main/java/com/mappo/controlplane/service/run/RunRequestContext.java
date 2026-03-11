package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import java.util.List;

public record RunRequestContext(
    CreateRunCommand command,
    ProjectExecutionCapabilities capabilities,
    ReleaseRecord release,
    List<TargetRecord> targets
) {
}
