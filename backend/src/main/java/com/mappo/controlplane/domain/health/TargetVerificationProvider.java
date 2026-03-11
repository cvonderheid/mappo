package com.mappo.controlplane.domain.health;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.TargetVerificationResultRecord;

public interface TargetVerificationProvider {

    boolean supports(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    );

    TargetVerificationResultRecord verify(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    );
}
