package com.mappo.controlplane.application.project.validation;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import java.util.List;

public interface ProjectWebhookValidator {

    boolean supports(ProjectDefinition project);

    List<ProjectValidationFindingRecord> validate(ProjectDefinition project);
}
