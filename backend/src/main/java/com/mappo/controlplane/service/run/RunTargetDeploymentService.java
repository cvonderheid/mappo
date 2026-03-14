package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.execution.DeploymentDriver;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ExternalExecutionHandleRecord;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.repository.RunTargetCommandRepository;
import java.util.Optional;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import org.springframework.stereotype.Service;

@Service
public class RunTargetDeploymentService {

    private final DeploymentDriverRegistry deploymentDriverRegistry;
    private final RunTargetCommandRepository runTargetCommandRepository;
    private final RunTargetStageService runTargetStageService;

    public RunTargetDeploymentService(
        DeploymentDriverRegistry deploymentDriverRegistry,
        RunTargetCommandRepository runTargetCommandRepository,
        RunTargetStageService runTargetStageService
    ) {
        this.deploymentDriverRegistry = deploymentDriverRegistry;
        this.runTargetCommandRepository = runTargetCommandRepository;
        this.runTargetStageService = runTargetStageService;
    }

    public DeploymentResult deploy(
        String runId,
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        TargetExecutionContextRecord context,
        ResolvedTargetAccessContext accessContext,
        boolean azureConfigured
    ) {
        var start = runTargetStageService.beginStage(
            runId,
            release.projectId(),
            context.targetId(),
            MappoTargetStage.DEPLOYING,
            "Deploying started."
        );

        try {
            TargetDeploymentOutcome outcome = deployOutcome(runId, capabilities, release, context, accessContext, azureConfigured);
            persistExternalExecutionHandle(runId, context.targetId(), outcome.externalExecutionHandle());
            var completion = new RunTargetStageService.StageStart(outcome.correlationId(), start.startedAt());
            runTargetStageService.completeStage(
                runId,
                release.projectId(),
                context.targetId(),
                MappoTargetStage.DEPLOYING,
                completion,
                outcome.message(),
                outcome.portalLink()
            );
            return DeploymentResult.success(outcome.correlationId());
        } catch (TargetDeploymentException error) {
            persistExternalExecutionHandle(runId, context.targetId(), error.getExternalExecutionHandle());
            String correlationId = error.getCorrelationId() == null || error.getCorrelationId().trim().isEmpty()
                ? start.correlationId()
                : error.getCorrelationId();
            runTargetStageService.failStage(
                runId,
                release.projectId(),
                context.targetId(),
                MappoTargetStage.DEPLOYING,
                correlationId,
                error.getMessage(),
                error.getError()
            );
            return DeploymentResult.failure();
        } catch (RuntimeException error) {
            runTargetStageService.failStage(
                runId,
                release.projectId(),
                context.targetId(),
                MappoTargetStage.DEPLOYING,
                start.correlationId(),
                "Deployment execution failed unexpectedly: " + error.getMessage(),
                new StageErrorRecord(
                    "TARGET_DEPLOYMENT_UNEXPECTED_ERROR",
                    "Deployment execution failed unexpectedly: " + error.getMessage(),
                    new StageErrorDetailsRecord(
                        null,
                        error.getClass().getName() + ": " + error.getMessage(),
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
            return DeploymentResult.failure();
        }
    }

    private TargetDeploymentOutcome deployOutcome(
        String runId,
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        TargetExecutionContextRecord context,
        ResolvedTargetAccessContext accessContext,
        boolean azureConfigured
    ) {
        Optional<DeploymentDriver> driver = capabilities.deploymentDriver();
        if (driver.isPresent()) {
            return driver.get().deploy(runId, capabilities.project(), release, context, accessContext);
        }
        return simulateDeployment(runId, release, context);
    }

    private TargetDeploymentOutcome simulateDeployment(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord context
    ) {
        if (context.simulatedFailureMode() == MappoSimulatedFailureMode.deploy_once) {
            String correlationId = runTargetStageService.correlationId(runId, context.targetId(), MappoTargetStage.DEPLOYING);
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

        String correlationId = runTargetStageService.correlationId(runId, context.targetId(), MappoTargetStage.DEPLOYING);
        return new TargetDeploymentOutcome(
            correlationId,
            "Simulator applied release " + release.sourceVersion() + " to " + context.targetId() + ".",
            "",
            new ExternalExecutionHandleRecord(
                "simulator",
                runId + ":" + context.targetId(),
                "simulated-deployment",
                "succeeded",
                null,
                null,
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            )
        );
    }

    private void persistExternalExecutionHandle(String runId, String targetId, ExternalExecutionHandleRecord handle) {
        if (handle == null) {
            return;
        }
        runTargetCommandRepository.upsertExternalExecutionHandle(runId, targetId, handle);
    }

    public record DeploymentResult(boolean succeeded, String correlationId) {
        static DeploymentResult success(String correlationId) {
            return new DeploymentResult(true, correlationId);
        }

        static DeploymentResult failure() {
            return new DeploymentResult(false, "");
        }
    }
}
