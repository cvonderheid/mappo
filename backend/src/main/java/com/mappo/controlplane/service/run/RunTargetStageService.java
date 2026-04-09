package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.persistence.run.RunTargetCommandRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class RunTargetStageService {

    private final RunTargetCommandRepository runTargetCommandRepository;
    private final LiveUpdateService liveUpdateService;

    public RunTargetStageService(
        RunTargetCommandRepository runTargetCommandRepository,
        LiveUpdateService liveUpdateService
    ) {
        this.runTargetCommandRepository = runTargetCommandRepository;
        this.liveUpdateService = liveUpdateService;
    }

    public StageStart beginStage(String runId, String projectId, String targetId, MappoTargetStage stage, String message) {
        String correlationId = correlationId(runId, targetId, stage);
        OffsetDateTime startedAt = now();
        runTargetCommandRepository.updateTargetExecutionStatus(runId, targetId, stage);
        runTargetCommandRepository.appendTargetLog(
            runId,
            targetId,
            MappoForwarderLogLevel.info,
            stage,
            startedAt,
            message,
            correlationId
        );
        publishRunChange(projectId, runId);
        return new StageStart(correlationId, startedAt);
    }

    public void completeStage(
        String runId,
        String projectId,
        String targetId,
        MappoTargetStage stage,
        StageStart start,
        String message,
        String portalLink
    ) {
        OffsetDateTime endedAt = now();
        runTargetCommandRepository.appendTargetStage(
            runId,
            targetId,
            stage,
            start.startedAt(),
            endedAt,
            message,
            null,
            start.correlationId(),
            normalize(portalLink)
        );
        runTargetCommandRepository.appendTargetLog(
            runId,
            targetId,
            MappoForwarderLogLevel.info,
            stage,
            endedAt,
            message,
            start.correlationId()
        );
        publishRunChange(projectId, runId);
    }

    public boolean failStage(
        String runId,
        String projectId,
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
            normalize(correlationId),
            ""
        );
        runTargetCommandRepository.appendTargetLog(
            runId,
            targetId,
            MappoForwarderLogLevel.error,
            stage,
            timestamp,
            message,
            normalize(correlationId)
        );
        publishRunChange(projectId, runId);
        return false;
    }

    public void markSucceeded(String runId, String projectId, String targetId, String correlationId) {
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
            normalize(correlationId),
            ""
        );
        runTargetCommandRepository.appendTargetLog(
            runId,
            targetId,
            MappoForwarderLogLevel.info,
            MappoTargetStage.SUCCEEDED,
            timestamp,
            "Target deployment succeeded.",
            normalize(correlationId)
        );
        publishRunChange(projectId, runId);
    }

    public String correlationId(String runId, String targetId, MappoTargetStage stage) {
        return "corr-" + runId + "-" + targetId + "-" + stage.name().toLowerCase();
    }

    private void publishRunChange(String projectId, String runId) {
        liveUpdateService.emitRunsUpdated(projectId);
        liveUpdateService.emitRunUpdated(projectId, runId);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record StageStart(String correlationId, OffsetDateTime startedAt) {
    }
}
