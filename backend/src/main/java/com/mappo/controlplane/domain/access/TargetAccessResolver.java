package com.mappo.controlplane.domain.access;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;

public interface TargetAccessResolver {

    boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured);

    TargetAccessValidation validate(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    );
}
