package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import org.springframework.stereotype.Service;

@Service
public class RunTargetExecutionService {

    public boolean failValidation(String runId, String projectId, String targetId, String message) {
        return runTargetValidationService.failMissingContext(runId, projectId, targetId, message);
    }

    private final RunTargetValidationService runTargetValidationService;
    private final RunTargetDeploymentService runTargetDeploymentService;
    private final RunTargetVerificationService runTargetVerificationService;
    private final RunTargetStageService runTargetStageService;

    public RunTargetExecutionService(
        RunTargetValidationService runTargetValidationService,
        RunTargetDeploymentService runTargetDeploymentService,
        RunTargetVerificationService runTargetVerificationService,
        RunTargetStageService runTargetStageService
    ) {
        this.runTargetValidationService = runTargetValidationService;
        this.runTargetDeploymentService = runTargetDeploymentService;
        this.runTargetVerificationService = runTargetVerificationService;
        this.runTargetStageService = runTargetStageService;
    }

    public boolean executeTarget(
        String runId,
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean runtimeConfigured
    ) {
        if (context == null) {
            return runTargetValidationService.failMissingContext(
                runId,
                target.projectId(),
                target.id(),
                "Target is missing registration metadata required for execution."
            );
        }

        TargetAccessValidation validation = runTargetValidationService.validate(
            runId,
            capabilities,
            release,
            target,
            context,
            runtimeConfigured
        );
        if (!validation.valid()) {
            return false;
        }

        RunTargetDeploymentService.DeploymentResult deployment = runTargetDeploymentService.deploy(
            runId,
            capabilities,
            release,
            context,
            validation.accessContext(),
            runtimeConfigured
        );
        if (!deployment.succeeded()) {
            return false;
        }

        if (!runTargetVerificationService.verify(runId, capabilities, release, target, context, runtimeConfigured)) {
            return false;
        }

        runTargetStageService.markSucceeded(runId, target.projectId(), target.id(), deployment.correlationId());
        return true;
    }
}
