package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import org.springframework.stereotype.Service;

@Service
public class RunTargetValidationService {

    private final TargetAccessResolverRegistry targetAccessResolverRegistry;
    private final RunExecutionPolicyService runExecutionPolicyService;
    private final RunTargetStageService runTargetStageService;

    public RunTargetValidationService(
        TargetAccessResolverRegistry targetAccessResolverRegistry,
        RunExecutionPolicyService runExecutionPolicyService,
        RunTargetStageService runTargetStageService
    ) {
        this.targetAccessResolverRegistry = targetAccessResolverRegistry;
        this.runExecutionPolicyService = runExecutionPolicyService;
        this.runTargetStageService = runTargetStageService;
    }

    public TargetAccessValidation validate(
        String runId,
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        var start = runTargetStageService.beginStage(
            runId,
            target.projectId(),
            target.id(),
            MappoTargetStage.VALIDATING,
            "Validating started."
        );

        if (runExecutionPolicyService.isSimulatorMode(capabilities.project(), release, azureConfigured)
            && context.simulatedFailureMode() == MappoSimulatedFailureMode.validate_once) {
            runTargetStageService.failStage(
                runId,
                target.projectId(),
                target.id(),
                MappoTargetStage.VALIDATING,
                start.correlationId(),
                "Simulated validation failure. Retry the run after clearing the target failure mode.",
                new StageErrorRecord(
                    "SIMULATED_VALIDATION_FAILED",
                    "Simulated validation failure. Retry the run after clearing the target failure mode.",
                    new StageErrorDetailsRecord(
                        null,
                        "simulated_failure_mode=validate_once",
                        null,
                        null,
                        null,
                        null,
                        null,
                        start.correlationId(),
                        null,
                        null,
                        context.containerAppResourceId()
                    )
                )
            );
            return TargetAccessValidation.failure(
                "Simulated validation failure. Retry the run after clearing the target failure mode.",
                null
            );
        }

        TargetAccessValidation validation = capabilities.targetAccessResolver()
            .validate(capabilities.project(), release, target, context, azureConfigured);
        if (!validation.valid()) {
            runTargetStageService.failStage(
                runId,
                target.projectId(),
                target.id(),
                MappoTargetStage.VALIDATING,
                start.correlationId(),
                validation.message(),
                validation.error()
            );
            return validation;
        }

        runTargetStageService.completeStage(
            runId,
            target.projectId(),
            target.id(),
            MappoTargetStage.VALIDATING,
            start,
            validation.message(),
            ""
        );
        return validation;
    }

    public boolean failMissingContext(String runId, String projectId, String targetId, String message) {
        String correlationId = runTargetStageService.correlationId(runId, targetId, MappoTargetStage.VALIDATING);
        return runTargetStageService.failStage(
            runId,
            projectId,
            targetId,
            MappoTargetStage.VALIDATING,
            correlationId,
            message,
            new StageErrorRecord(
                "TARGET_CONFIGURATION_INVALID",
                message,
                new StageErrorDetailsRecord(
                    null,
                    message,
                    null,
                    null,
                    null,
                    null,
                    null,
                    correlationId,
                    null,
                    null,
                    null
                )
            )
        );
    }
}
