package com.mappo.controlplane.application.project.validation;

import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.ProjectValidationFindingStatus;
import com.mappo.controlplane.model.ProjectValidationScope;

public final class ProjectValidationFindingFactory {

    private ProjectValidationFindingFactory() {
    }

    public static ProjectValidationFindingRecord pass(ProjectValidationScope scope, String code, String message) {
        return new ProjectValidationFindingRecord(scope, ProjectValidationFindingStatus.pass, code, message);
    }

    public static ProjectValidationFindingRecord warning(ProjectValidationScope scope, String code, String message) {
        return new ProjectValidationFindingRecord(scope, ProjectValidationFindingStatus.warning, code, message);
    }

    public static ProjectValidationFindingRecord fail(ProjectValidationScope scope, String code, String message) {
        return new ProjectValidationFindingRecord(scope, ProjectValidationFindingStatus.fail, code, message);
    }
}
