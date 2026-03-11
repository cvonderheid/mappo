package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.access.TargetAccessResolver;
import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
public class DefaultTargetAccessResolver implements TargetAccessResolver {

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured) {
        return true;
    }

    @Override
    public TargetAccessValidation validate(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        if (context == null) {
            return TargetAccessValidation.failure(
                "Target is missing registration metadata required for execution.",
                invalidTargetConfiguration(
                    "Target is missing registration metadata required for execution.",
                    null,
                    null
                )
            );
        }
        return TargetAccessValidation.success("Validated target " + target.id() + " for simulator execution.");
    }

    public static StageErrorRecord invalidTargetConfiguration(
        String message,
        String detail,
        String containerAppResourceId
    ) {
        return new StageErrorRecord(
            "TARGET_CONFIGURATION_INVALID",
            message,
            new StageErrorDetailsRecord(
                null,
                detail == null || detail.isBlank() ? message : detail,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                containerAppResourceId
            )
        );
    }
}
