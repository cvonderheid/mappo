package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.execution.DeploymentDriver;
import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.repository.RunTargetCommandRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class RunTargetExecutionService {

    private final RunTargetCommandRepository runTargetCommandRepository;
    private final DeploymentDriverRegistry deploymentDriverRegistry;
    private final RunExecutionPolicyService runExecutionPolicyService;
    private final LiveUpdateService liveUpdateService;

    public RunTargetExecutionService(
        RunTargetCommandRepository runTargetCommandRepository,
        DeploymentDriverRegistry deploymentDriverRegistry,
        RunExecutionPolicyService runExecutionPolicyService,
        LiveUpdateService liveUpdateService
    ) {
        this.runTargetCommandRepository = runTargetCommandRepository;
        this.deploymentDriverRegistry = deploymentDriverRegistry;
        this.runExecutionPolicyService = runExecutionPolicyService;
        this.liveUpdateService = liveUpdateService;
    }

    public boolean executeTarget(
        String runId,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        if (context == null) {
            return failValidation(runId, target.id(), "Target is missing registration metadata required for execution.");
        }

        if (!validateTarget(runId, release, target, context, azureConfigured)) {
            return false;
        }

        DeploymentOutcome deployment = deployTarget(runId, release, target, context, azureConfigured);
        if (!deployment.succeeded()) {
            return false;
        }

        if (!verifyTarget(runId, release, target, azureConfigured)) {
            return false;
        }

        markSucceeded(runId, target.id(), deployment.correlationId());
        return true;
    }

    public boolean failValidation(String runId, String targetId, String message) {
        String correlationId = correlationId(runId, targetId, MappoTargetStage.VALIDATING);
        return failStage(
            runId,
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

    private boolean validateTarget(
        String runId,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        String correlationId = correlationId(runId, target.id(), MappoTargetStage.VALIDATING);
        OffsetDateTime startedAt = now();

        runTargetCommandRepository.updateTargetExecutionStatus(runId, target.id(), MappoTargetStage.VALIDATING);
        runTargetCommandRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.VALIDATING,
            startedAt,
            "Validating started.",
            correlationId
        );
        publishRunChange(runId);

        if (runExecutionPolicyService.isSimulatorMode(release, azureConfigured)
            && context.simulatedFailureMode() == MappoSimulatedFailureMode.validate_once) {
            return failStage(
                runId,
                target.id(),
                MappoTargetStage.VALIDATING,
                correlationId,
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
                        correlationId,
                        null,
                        null,
                        context.containerAppResourceId()
                    )
                )
            );
        }

        if (runExecutionPolicyService.useRealTemplateSpecExecution(release, azureConfigured)
            && blank(context.managedResourceGroupId())) {
            return failStage(
                runId,
                target.id(),
                MappoTargetStage.VALIDATING,
                correlationId,
                "Target is missing managedResourceGroupId required for Template Spec execution.",
                new StageErrorRecord(
                    "TARGET_CONFIGURATION_INVALID",
                    "Target is missing managedResourceGroupId required for Template Spec execution.",
                    new StageErrorDetailsRecord(
                        null,
                        "managedResourceGroupId is blank",
                        null,
                        null,
                        null,
                        null,
                        null,
                        correlationId,
                        null,
                        null,
                        context.containerAppResourceId()
                    )
                )
            );
        }

        if (runExecutionPolicyService.useRealDeploymentStackExecution(release, azureConfigured)
            && (blank(context.managedResourceGroupId()) || blank(context.containerAppResourceId()))) {
            return failStage(
                runId,
                target.id(),
                MappoTargetStage.VALIDATING,
                correlationId,
                "Target is missing execution metadata required for deployment_stack execution.",
                new StageErrorRecord(
                    "TARGET_CONFIGURATION_INVALID",
                    "Target is missing execution metadata required for deployment_stack execution.",
                    new StageErrorDetailsRecord(
                        null,
                        "managedResourceGroupId or containerAppResourceId is blank",
                        null,
                        null,
                        null,
                        null,
                        null,
                        correlationId,
                        null,
                        null,
                        context.containerAppResourceId()
                    )
                )
            );
        }

        OffsetDateTime endedAt = now();
        String message = runExecutionPolicyService.validationMessage(release, target, context, azureConfigured);
        runTargetCommandRepository.appendTargetStage(
            runId,
            target.id(),
            MappoTargetStage.VALIDATING,
            startedAt,
            endedAt,
            message,
            null,
            correlationId,
            ""
        );
        runTargetCommandRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.VALIDATING,
            endedAt,
            message,
            correlationId
        );
        publishRunChange(runId);
        return true;
    }

    private DeploymentOutcome deployTarget(
        String runId,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        String correlationId = correlationId(runId, target.id(), MappoTargetStage.DEPLOYING);
        OffsetDateTime startedAt = now();

        runTargetCommandRepository.updateTargetExecutionStatus(runId, target.id(), MappoTargetStage.DEPLOYING);
        runTargetCommandRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.DEPLOYING,
            startedAt,
            "Deploying started.",
            correlationId
        );
        publishRunChange(runId);

        try {
            TargetDeploymentOutcome outcome = deployOutcome(runId, release, context, azureConfigured);

            OffsetDateTime endedAt = now();
            runTargetCommandRepository.appendTargetStage(
                runId,
                target.id(),
                MappoTargetStage.DEPLOYING,
                startedAt,
                endedAt,
                outcome.message(),
                null,
                outcome.correlationId(),
                outcome.portalLink()
            );
            runTargetCommandRepository.appendTargetLog(
                runId,
                target.id(),
                MappoForwarderLogLevel.info,
                MappoTargetStage.DEPLOYING,
                endedAt,
                outcome.message(),
                outcome.correlationId()
            );
            publishRunChange(runId);
            return DeploymentOutcome.success(outcome.correlationId());
        } catch (TargetDeploymentException error) {
            failStage(
                runId,
                target.id(),
                MappoTargetStage.DEPLOYING,
                blank(error.getCorrelationId()) ? correlationId : error.getCorrelationId(),
                error.getMessage(),
                error.getError()
            );
            return DeploymentOutcome.failure();
        }
    }

    private boolean verifyTarget(
        String runId,
        ReleaseRecord release,
        TargetRecord target,
        boolean azureConfigured
    ) {
        String correlationId = correlationId(runId, target.id(), MappoTargetStage.VERIFYING);
        OffsetDateTime startedAt = now();

        runTargetCommandRepository.updateTargetExecutionStatus(runId, target.id(), MappoTargetStage.VERIFYING);
        runTargetCommandRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.VERIFYING,
            startedAt,
            "Verifying started.",
            correlationId
        );
        publishRunChange(runId);

        if (runExecutionPolicyService.isSimulatorMode(release, azureConfigured)
            && target.simulatedFailureMode() == MappoSimulatedFailureMode.verify_once) {
            return failStage(
                runId,
                target.id(),
                MappoTargetStage.VERIFYING,
                correlationId,
                "Simulated verification failure. Retry the run after clearing the target failure mode.",
                new StageErrorRecord(
                    "SIMULATED_VERIFICATION_FAILED",
                    "Simulated verification failure. Retry the run after clearing the target failure mode.",
                    new StageErrorDetailsRecord(
                        null,
                        "simulated_failure_mode=verify_once",
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

        OffsetDateTime endedAt = now();
        String message = release.executionSettings().verifyAfterDeploy()
            ? runExecutionPolicyService.verificationMessage(release, azureConfigured)
            : "Verification skipped by release settings.";
        runTargetCommandRepository.appendTargetStage(
            runId,
            target.id(),
            MappoTargetStage.VERIFYING,
            startedAt,
            endedAt,
            message,
            null,
            correlationId,
            ""
        );
        runTargetCommandRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.VERIFYING,
            endedAt,
            message,
            correlationId
        );
        publishRunChange(runId);
        return true;
    }

    private void markSucceeded(String runId, String targetId, String correlationId) {
        OffsetDateTime timestamp = now();
        runTargetCommandRepository.updateTargetExecutionStatus(runId, targetId, MappoTargetStage.SUCCEEDED);
        runTargetCommandRepository.appendTargetStage(
            runId,
            targetId,
            MappoTargetStage.SUCCEEDED,
            timestamp,
            timestamp,
            "Target deployment succeeded.",
            null,
            correlationId,
            ""
        );
        runTargetCommandRepository.appendTargetLog(
            runId,
            targetId,
            MappoForwarderLogLevel.info,
            MappoTargetStage.SUCCEEDED,
            timestamp,
            "Target deployment succeeded.",
            correlationId
        );
        publishRunChange(runId);
    }

    private boolean failStage(
        String runId,
        String targetId,
        MappoTargetStage stage,
        String correlationId,
        String message,
        StageErrorRecord error
    ) {
        OffsetDateTime timestamp = now();
        runTargetCommandRepository.updateTargetExecutionStatus(runId, targetId, MappoTargetStage.FAILED);
        runTargetCommandRepository.appendTargetStage(
            runId,
            targetId,
            stage,
            timestamp,
            timestamp,
            message,
            error,
            correlationId,
            ""
        );
        runTargetCommandRepository.appendTargetLog(
            runId,
            targetId,
            MappoForwarderLogLevel.error,
            stage,
            timestamp,
            message,
            correlationId
        );
        publishRunChange(runId);
        return false;
    }

    private TargetDeploymentOutcome simulateDeployment(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord context
    ) {
        if (context.simulatedFailureMode() == MappoSimulatedFailureMode.deploy_once) {
            String correlationId = correlationId(runId, context.targetId(), MappoTargetStage.DEPLOYING);
            throw new TargetDeploymentException(
                "Simulated deployment failure. Retry the run after clearing the target failure mode.",
                new StageErrorRecord(
                    "SIMULATED_DEPLOYMENT_FAILED",
                    "Simulated deployment failure. Retry the run after clearing the target failure mode.",
                    new StageErrorDetailsRecord(
                        null,
                        "simulated_failure_mode=deploy_once",
                        null,
                        null,
                        null,
                        null,
                        null,
                        correlationId,
                        null,
                        null,
                        context.containerAppResourceId()
                    )
                ),
                correlationId,
                ""
            );
        }

        String correlationId = correlationId(runId, context.targetId(), MappoTargetStage.DEPLOYING);
        return new TargetDeploymentOutcome(
            correlationId,
            "Simulator applied release " + release.sourceVersion() + " to " + context.targetId() + ".",
            ""
        );
    }

    private TargetDeploymentOutcome deployOutcome(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        java.util.Optional<DeploymentDriver> driver = deploymentDriverRegistry.findDriver(release, azureConfigured);
        if (driver.isPresent()) {
            return driver.get().deploy(runId, release, context);
        }
        return simulateDeployment(runId, release, context);
    }

    private void publishRunChange(String runId) {
        liveUpdateService.emitRunsUpdated();
        liveUpdateService.emitRunUpdated(runId);
    }

    private String correlationId(String runId, String targetId, MappoTargetStage stage) {
        return "corr-" + runId + "-" + targetId + "-" + stage.name().toLowerCase();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record DeploymentOutcome(boolean succeeded, String correlationId) {
        static DeploymentOutcome success(String correlationId) {
            return new DeploymentOutcome(true, correlationId);
        }

        static DeploymentOutcome failure() {
            return new DeploymentOutcome(false, "");
        }
    }
}
