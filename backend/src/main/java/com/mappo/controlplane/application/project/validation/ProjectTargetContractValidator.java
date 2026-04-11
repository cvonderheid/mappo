package com.mappo.controlplane.application.project.validation;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import java.util.List;

public interface ProjectTargetContractValidator {

    boolean supports(ProjectDefinition project);

    List<ProjectValidationFindingRecord> validate(
        ProjectDefinition project,
        TargetRecord target,
        TargetExecutionContextRecord context
    );
}
