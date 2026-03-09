package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.repository.RunRepository;
import com.mappo.controlplane.repository.TargetRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunExecutionService {

    private final RunRepository runRepository;
    private final TargetRepository targetRepository;
    private final TemplateSpecExecutor templateSpecExecutor;
    private final DeploymentStackExecutor deploymentStackExecutor;
    private final RunExecutionPolicyService runExecutionPolicyService;

    public void executeRun(
        RunDetailRecord run,
        ReleaseRecord release,
        List<TargetRecord> targets,
        boolean azureConfigured
    ) {
        String runId = run.id();
        List<TargetExecutionContextRecord> contexts = loadExecutionContexts(targets);
        Map<String, TargetExecutionContextRecord> contextsByTarget = indexContexts(contexts);
        List<String> warnings = runExecutionPolicyService.buildWarnings(release, targets, azureConfigured);

        runRepository.deleteRunWarnings(runId);
        for (int i = 0; i < warnings.size(); i++) {
            runRepository.addRunWarning(runId, i, warnings.get(i));
        }

        int failedCount = 0;
        int succeededCount = 0;
        String haltReason = null;
        int processedCount = 0;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<List<TargetRecord>> batches = runExecutionPolicyService.planBatches(run, targets);
            for (List<TargetRecord> batch : batches) {
                List<TargetRunResult> batchResults = executeBatch(
                    executor,
                    runId,
                    release,
                    batch,
                    contextsByTarget,
                    azureConfigured
                );
                processedCount += batchResults.size();
                for (TargetRunResult result : batchResults) {
                    if (result.succeeded()) {
                        succeededCount += 1;
                        targetRepository.updateLastDeployedRelease(result.targetId(), release.sourceVersion());
                    } else {
                        failedCount += 1;
                    }
                }

                haltReason = runExecutionPolicyService.haltReasonFor(
                    run.stopPolicy(),
                    failedCount,
                    processedCount,
                    targets.size()
                );
                if (haltReason != null && processedCount < targets.size()) {
                    break;
                }
            }
        }

        runRepository.markRunComplete(
            runId,
            runExecutionPolicyService.finalRunStatus(
                targets.size(),
                succeededCount,
                failedCount,
                haltReason,
                processedCount
            ),
            haltReason
        );
    }

    private List<TargetRunResult> executeBatch(
        java.util.concurrent.ExecutorService executor,
        String runId,
        ReleaseRecord release,
        List<TargetRecord> batch,
        Map<String, TargetExecutionContextRecord> contextsByTarget,
        boolean azureConfigured
    ) {
        List<Future<TargetRunResult>> futures = new ArrayList<>(batch.size());
        for (TargetRecord target : batch) {
            futures.add(executor.submit(() -> {
                TargetExecutionContextRecord context = contextsByTarget.get(target.id());
                TargetRunOutcome outcome = executeTarget(runId, release, target, context, azureConfigured);
                return new TargetRunResult(target.id(), outcome.succeeded());
            }));
        }

        List<TargetRunResult> results = new ArrayList<>(batch.size());
        for (Future<TargetRunResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Run execution interrupted", error);
            } catch (ExecutionException error) {
                throw new IllegalStateException("Run execution worker failed", error.getCause());
            }
        }
        return results;
    }

    private TargetRunOutcome executeTarget(
        String runId,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        if (context == null) {
            return failValidation(runId, target.id(), "Target is missing registration metadata required for execution.");
        }

        ValidationOutcome validation = validateTarget(runId, release, target, context, azureConfigured);
        if (!validation.succeeded()) {
            return TargetRunOutcome.failure();
        }

        DeploymentOutcome deployment = deployTarget(runId, release, target, context, azureConfigured);
        if (!deployment.succeeded()) {
            return TargetRunOutcome.failure();
        }

        VerificationOutcome verification = verifyTarget(runId, release, target, azureConfigured);
        if (!verification.succeeded()) {
            return TargetRunOutcome.failure();
        }

        markSucceeded(runId, target.id(), deployment.correlationId());
        return TargetRunOutcome.success();
    }

    private ValidationOutcome validateTarget(
        String runId,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        String correlationId = correlationId(runId, target.id(), MappoTargetStage.VALIDATING);
        OffsetDateTime startedAt = now();

        runRepository.updateTargetExecutionStatus(runId, target.id(), MappoTargetStage.VALIDATING);
        runRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.VALIDATING,
            startedAt,
            "Validating started.",
            correlationId
        );

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
        runRepository.appendTargetStage(
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
        runRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.VALIDATING,
            endedAt,
            message,
            correlationId
        );
        return ValidationOutcome.success();
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

        runRepository.updateTargetExecutionStatus(runId, target.id(), MappoTargetStage.DEPLOYING);
        runRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.DEPLOYING,
            startedAt,
            "Deploying started.",
            correlationId
        );

        try {
            TargetDeploymentOutcome outcome = deployOutcome(runId, release, context, azureConfigured);

            OffsetDateTime endedAt = now();
            runRepository.appendTargetStage(
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
            runRepository.appendTargetLog(
                runId,
                target.id(),
                MappoForwarderLogLevel.info,
                MappoTargetStage.DEPLOYING,
                endedAt,
                outcome.message(),
                outcome.correlationId()
            );
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

    private VerificationOutcome verifyTarget(
        String runId,
        ReleaseRecord release,
        TargetRecord target,
        boolean azureConfigured
    ) {
        String correlationId = correlationId(runId, target.id(), MappoTargetStage.VERIFYING);
        OffsetDateTime startedAt = now();

        runRepository.updateTargetExecutionStatus(runId, target.id(), MappoTargetStage.VERIFYING);
        runRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.VERIFYING,
            startedAt,
            "Verifying started.",
            correlationId
        );

        if (runExecutionPolicyService.isSimulatorMode(release, azureConfigured)
            && target.simulatedFailureMode() == MappoSimulatedFailureMode.verify_once) {
            failStage(
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
            return VerificationOutcome.failure();
        }

        OffsetDateTime endedAt = now();
        String message = release.executionSettings().verifyAfterDeploy()
            ? runExecutionPolicyService.verificationMessage(release, azureConfigured)
            : "Verification skipped by release settings.";
        runRepository.appendTargetStage(
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
        runRepository.appendTargetLog(
            runId,
            target.id(),
            MappoForwarderLogLevel.info,
            MappoTargetStage.VERIFYING,
            endedAt,
            message,
            correlationId
        );
        return VerificationOutcome.success();
    }

    private void markSucceeded(String runId, String targetId, String correlationId) {
        OffsetDateTime timestamp = now();
        runRepository.updateTargetExecutionStatus(runId, targetId, MappoTargetStage.SUCCEEDED);
        runRepository.appendTargetStage(
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
        runRepository.appendTargetLog(
            runId,
            targetId,
            MappoForwarderLogLevel.info,
            MappoTargetStage.SUCCEEDED,
            timestamp,
            "Target deployment succeeded.",
            correlationId
        );
    }

    private TargetRunOutcome failValidation(String runId, String targetId, String message) {
        String correlationId = correlationId(runId, targetId, MappoTargetStage.VALIDATING);
        failStage(
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
        return TargetRunOutcome.failure();
    }

    private ValidationOutcome failStage(
        String runId,
        String targetId,
        MappoTargetStage stage,
        String correlationId,
        String message,
        StageErrorRecord error
    ) {
        OffsetDateTime timestamp = now();
        runRepository.updateTargetExecutionStatus(runId, targetId, MappoTargetStage.FAILED);
        runRepository.appendTargetStage(
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
        runRepository.appendTargetLog(
            runId,
            targetId,
            MappoForwarderLogLevel.error,
            stage,
            timestamp,
            message,
            correlationId
        );
        return ValidationOutcome.failure();
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

    private List<TargetExecutionContextRecord> loadExecutionContexts(List<TargetRecord> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        return targetRepository.getExecutionContextsByIds(targets.stream().map(TargetRecord::id).toList());
    }

    private Map<String, TargetExecutionContextRecord> indexContexts(List<TargetExecutionContextRecord> contexts) {
        Map<String, TargetExecutionContextRecord> index = new LinkedHashMap<>();
        for (TargetExecutionContextRecord context : contexts) {
            index.put(context.targetId(), context);
        }
        return index;
    }

    private TargetDeploymentOutcome deployOutcome(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        if (runExecutionPolicyService.useRealTemplateSpecExecution(release, azureConfigured)) {
            return templateSpecExecutor.deploy(runId, release, context);
        }
        if (runExecutionPolicyService.useRealDeploymentStackExecution(release, azureConfigured)) {
            return deploymentStackExecutor.deploy(runId, release, context);
        }
        return simulateDeployment(runId, release, context);
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

    private record TargetRunOutcome(boolean succeeded) {
        static TargetRunOutcome success() {
            return new TargetRunOutcome(true);
        }

        static TargetRunOutcome failure() {
            return new TargetRunOutcome(false);
        }
    }

    private record TargetRunResult(String targetId, boolean succeeded) {
    }

    private record ValidationOutcome(boolean succeeded) {
        static ValidationOutcome success() {
            return new ValidationOutcome(true);
        }

        static ValidationOutcome failure() {
            return new ValidationOutcome(false);
        }
    }

    private record DeploymentOutcome(boolean succeeded, String correlationId) {
        static DeploymentOutcome success(String correlationId) {
            return new DeploymentOutcome(true, correlationId);
        }

        static DeploymentOutcome failure() {
            return new DeploymentOutcome(false, "");
        }
    }

    private record VerificationOutcome(boolean succeeded) {
        static VerificationOutcome success() {
            return new VerificationOutcome(true);
        }

        static VerificationOutcome failure() {
            return new VerificationOutcome(false);
        }
    }
}
