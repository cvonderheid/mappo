package com.mappo.controlplane.domain.execution;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunPreviewMode;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.TargetPreviewOutcome;

public interface DeploymentPreviewDriver {

    boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured);

    RunPreviewMode mode();

    TargetPreviewOutcome preview(ProjectDefinition project, ReleaseRecord release, TargetExecutionContextRecord target);
}
