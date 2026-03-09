package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunStopPolicyRecord;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunExecutionService {

    private final RunRepository runRepository;
    private final TargetRepository targetRepository;
    private final TemplateSpecExecutor templateSpecExecutor;
    private final DeploymentStackExecutor deploymentStackExecutor;

    public void executeRun(
        String runId,
        ReleaseRecord release,
        List<TargetRecord> targets,
        boolean azureConfigured,
        RunStopPolicyRecord stopPolicy
    ) {
        List<TargetExecutionContextRecord> contexts = loadExecutionContexts(targets);
        Map<String, TargetExecutionContextRecord> contextsByTarget = indexContexts(contexts);
        List<String> warnings = buildWarnings(release, targets, azureConfigured);

        runRepository.deleteRunWarnings(runId);
        for (int i = 0; i < warnings.size(); i++) {
            runRepository.addRunWarning(runId, i, warnings.get(i));
        }

        int failedCount = 0;
        int succeededCount = 0;
        String haltReason = null;

        for (int i = 0; i < targets.size(); i++) {
            TargetRecord target = targets.get(i);
            TargetExecutionContextRecord context = contextsByTarget.get(target.id());
            TargetRunOutcome outcome = executeTarget(runId, release, target, context, azureConfigured);

            if (outcome.succeeded()) {
                succeededCount += 1;
                targetRepository.updateLastDeployedRelease(target.id(), release.sourceVersion());
            } else {
                failedCount += 1;
                haltReason = haltReasonFor(stopPolicy, failedCount, i + 1, targets.size());
                if (haltReason != null && i + 1 < targets.size()) {
                    break;
                }
            }
        }

        runRepository.markRunComplete(
            runId,
            finalRunStatus(targets.size(), succeededCount, failedCount, haltReason),
            haltReason
        );
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

        if (isSimulatorMode(release, azureConfigured)
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

        if (useRealTemplateSpecExecution(release, azureConfigured)
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

        if (useRealDeploymentStackExecution(release, azureConfigured)
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
        String message = validationMessage(release, target, context, azureConfigured);
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

        if (isSimulatorMode(release, azureConfigured)
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
            ? verificationMessage(release, azureConfigured)
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

    private List<String> buildWarnings(ReleaseRecord release, List<TargetRecord> targets, boolean azureConfigured) {
        List<String> warnings = new ArrayList<>();
        if (useRealExecution(release, azureConfigured) && release.executionSettings().whatIfOnCanary()) {
            warnings.add("whatIfOnCanary is configured but not implemented yet; live deployment will proceed directly.");
        }
        if (useRealExecution(release, azureConfigured)) {
            return warnings;
        }

        if (!azureConfigured) {
            warnings.add("Azure execution is not configured; run completed in simulator mode for source type " + sourceTypeLiteral(release) + ".");
            return warnings;
        }

        if (release.sourceType() == MappoReleaseSourceType.template_spec) {
            warnings.add(
                "Azure execution for source type template_spec with deployment scope "
                    + release.deploymentScope().getLiteral()
                    + " is not implemented yet; run completed in simulator mode."
            );
            return warnings;
        }

        warnings.add("Azure execution for source type " + sourceTypeLiteral(release) + " is not implemented yet; run completed in simulator mode.");
        if (hasTagValue(targets, "ring", "canary") && release.executionSettings().whatIfOnCanary()) {
            warnings.add("whatIfOnCanary is configured but not implemented in simulator mode.");
        }
        return warnings;
    }

    private boolean useRealTemplateSpecExecution(ReleaseRecord release, boolean azureConfigured) {
        return azureConfigured
            && release.sourceType() == MappoReleaseSourceType.template_spec
            && release.deploymentScope().getLiteral().equals("resource_group");
    }

    private boolean useRealDeploymentStackExecution(ReleaseRecord release, boolean azureConfigured) {
        return azureConfigured
            && release.sourceType() == MappoReleaseSourceType.deployment_stack
            && release.deploymentScope().getLiteral().equals("resource_group");
    }

    private boolean useRealExecution(ReleaseRecord release, boolean azureConfigured) {
        return useRealTemplateSpecExecution(release, azureConfigured)
            || useRealDeploymentStackExecution(release, azureConfigured);
    }

    private boolean isSimulatorMode(ReleaseRecord release, boolean azureConfigured) {
        return !useRealExecution(release, azureConfigured);
    }

    private boolean hasTagValue(List<TargetRecord> targets, String key, String expected) {
        for (TargetRecord target : targets) {
            String value = target.tags().get(key);
            if (expected.equalsIgnoreCase(blankToEmpty(value))) {
                return true;
            }
        }
        return false;
    }

    private String validationMessage(
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        if (useRealTemplateSpecExecution(release, azureConfigured)) {
            return "Validated target " + target.id() + "; deploying into resource group " + resourceGroupName(context.managedResourceGroupId()) + ".";
        }
        if (useRealDeploymentStackExecution(release, azureConfigured)) {
            return "Validated target " + target.id() + "; updating deployment stack scope " + context.managedResourceGroupId() + ".";
        }
        return "Validated target " + target.id() + " for simulator execution.";
    }

    private String verificationMessage(ReleaseRecord release, boolean azureConfigured) {
        if (useRealTemplateSpecExecution(release, azureConfigured)) {
            return "Verification passed: ARM deployment completed successfully.";
        }
        if (useRealDeploymentStackExecution(release, azureConfigured)) {
            return "Verification passed: deployment stack completed successfully.";
        }
        return "Verification passed in simulator mode.";
    }

    private TargetDeploymentOutcome deployOutcome(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        if (useRealTemplateSpecExecution(release, azureConfigured)) {
            return templateSpecExecutor.deploy(runId, release, context);
        }
        if (useRealDeploymentStackExecution(release, azureConfigured)) {
            return deploymentStackExecutor.deploy(runId, release, context);
        }
        return simulateDeployment(runId, release, context);
    }

    private String haltReasonFor(RunStopPolicyRecord stopPolicy, int failedCount, int processedCount, int totalTargets) {
        if (stopPolicy == null) {
            return null;
        }
        if (stopPolicy.maxFailureCount() != null && failedCount >= stopPolicy.maxFailureCount()) {
            return "failed targets " + failedCount + " reached threshold " + stopPolicy.maxFailureCount() + ".";
        }
        if (stopPolicy.maxFailureRate() != null && processedCount > 0) {
            double rate = (double) failedCount / (double) processedCount;
            if (rate >= stopPolicy.maxFailureRate()) {
                return "failure rate " + failedCount + "/" + processedCount + " reached threshold " + stopPolicy.maxFailureRate() + ".";
            }
        }
        return null;
    }

    private MappoRunStatus finalRunStatus(int targetCount, int succeededCount, int failedCount, String haltReason) {
        if (haltReason != null) {
            return MappoRunStatus.halted;
        }
        if (failedCount == 0) {
            return MappoRunStatus.succeeded;
        }
        if (succeededCount == 0 && failedCount >= targetCount) {
            return MappoRunStatus.failed;
        }
        return MappoRunStatus.partial;
    }

    private String correlationId(String runId, String targetId, MappoTargetStage stage) {
        return "corr-" + runId + "-" + targetId + "-" + stage.name().toLowerCase();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String resourceGroupName(String resourceId) {
        String value = blankToEmpty(resourceId);
        int index = value.toLowerCase().indexOf("/resourcegroups/");
        if (index < 0) {
            return value;
        }
        String suffix = value.substring(index + "/resourceGroups/".length());
        int slash = suffix.indexOf('/');
        return slash < 0 ? suffix : suffix.substring(0, slash);
    }

    private String sourceTypeLiteral(ReleaseRecord release) {
        return release.sourceType() == null ? "template_spec" : release.sourceType().getLiteral();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record TargetRunOutcome(boolean succeeded) {
        static TargetRunOutcome success() {
            return new TargetRunOutcome(true);
        }

        static TargetRunOutcome failure() {
            return new TargetRunOutcome(false);
        }
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
