package com.mappo.controlplane.persistence.run;

import static com.mappo.controlplane.jooq.Tables.RUNS;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_RECORDS;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXTERNAL_EXECUTION_HANDLES;
import static com.mappo.controlplane.jooq.Tables.TARGET_LOG_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGET_STAGE_RECORDS;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.ExternalExecutionHandleRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunTargetCommandRepository {

    private final DSLContext dsl;
    private final RunExecutionStateRepository runExecutionStateRepository;

    public void updateTargetExecutionStatus(String runId, String targetId, MappoTargetStage status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.update(TARGET_EXECUTION_RECORDS)
            .set(TARGET_EXECUTION_RECORDS.STATUS, enumOrDefault(status, MappoTargetStage.QUEUED))
            .set(TARGET_EXECUTION_RECORDS.UPDATED_AT, now)
            .where(TARGET_EXECUTION_RECORDS.RUN_ID.eq(runId))
            .and(TARGET_EXECUTION_RECORDS.TARGET_ID.eq(targetId))
            .execute();
        touchRun(runId, now);
    }

    public void appendTargetStage(
        String runId,
        String targetId,
        MappoTargetStage stage,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String message,
        StageErrorRecord error,
        String correlationId,
        String portalLink
    ) {
        StageErrorDetailsRecord details = error == null ? null : error.details();
        dsl.insertInto(TARGET_STAGE_RECORDS)
            .set(TARGET_STAGE_RECORDS.RUN_ID, runId)
            .set(TARGET_STAGE_RECORDS.TARGET_ID, targetId)
            .set(TARGET_STAGE_RECORDS.POSITION, nextStagePosition(runId, targetId))
            .set(TARGET_STAGE_RECORDS.STAGE, stage)
            .set(TARGET_STAGE_RECORDS.STARTED_AT, startedAt)
            .set(TARGET_STAGE_RECORDS.ENDED_AT, endedAt)
            .set(TARGET_STAGE_RECORDS.MESSAGE, nullableText(message) == null ? "" : message)
            .set(TARGET_STAGE_RECORDS.ERROR_CODE, error == null ? null : error.code())
            .set(TARGET_STAGE_RECORDS.ERROR_MESSAGE, error == null ? null : error.message())
            .set(TARGET_STAGE_RECORDS.ERROR_STATUS_CODE, details == null ? null : details.statusCode())
            .set(TARGET_STAGE_RECORDS.ERROR_DETAIL_TEXT, details == null ? null : details.error())
            .set(TARGET_STAGE_RECORDS.ERROR_DESIRED_IMAGE, details == null ? null : details.desiredImage())
            .set(TARGET_STAGE_RECORDS.AZURE_ERROR_CODE, details == null ? null : details.azureErrorCode())
            .set(TARGET_STAGE_RECORDS.AZURE_ERROR_MESSAGE, details == null ? null : details.azureErrorMessage())
            .set(TARGET_STAGE_RECORDS.AZURE_REQUEST_ID, details == null ? null : details.azureRequestId())
            .set(TARGET_STAGE_RECORDS.AZURE_ARM_SERVICE_REQUEST_ID, details == null ? null : details.azureArmServiceRequestId())
            .set(TARGET_STAGE_RECORDS.AZURE_CORRELATION_ID, details == null ? null : details.azureCorrelationId())
            .set(TARGET_STAGE_RECORDS.AZURE_DEPLOYMENT_NAME, details == null ? null : details.azureDeploymentName())
            .set(TARGET_STAGE_RECORDS.AZURE_OPERATION_ID, details == null ? null : details.azureOperationId())
            .set(TARGET_STAGE_RECORDS.AZURE_RESOURCE_ID, details == null ? null : details.azureResourceId())
            .set(TARGET_STAGE_RECORDS.CORRELATION_ID, normalize(correlationId))
            .set(TARGET_STAGE_RECORDS.PORTAL_LINK, normalize(portalLink))
            .execute();
        touchRun(runId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public void appendTargetLog(
        String runId,
        String targetId,
        MappoForwarderLogLevel level,
        MappoTargetStage stage,
        OffsetDateTime timestamp,
        String message,
        String correlationId
    ) {
        dsl.insertInto(TARGET_LOG_EVENTS)
            .set(TARGET_LOG_EVENTS.RUN_ID, runId)
            .set(TARGET_LOG_EVENTS.TARGET_ID, targetId)
            .set(TARGET_LOG_EVENTS.POSITION, nextLogPosition(runId, targetId))
            .set(TARGET_LOG_EVENTS.EVENT_TIMESTAMP, timestamp)
            .set(TARGET_LOG_EVENTS.LEVEL, enumOrDefault(level, MappoForwarderLogLevel.info))
            .set(TARGET_LOG_EVENTS.STAGE, enumOrDefault(stage, MappoTargetStage.QUEUED))
            .set(TARGET_LOG_EVENTS.MESSAGE, nullableText(message) == null ? "" : message)
            .set(TARGET_LOG_EVENTS.CORRELATION_ID, normalize(correlationId))
            .execute();
        touchRun(runId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public void upsertExternalExecutionHandle(String runId, String targetId, ExternalExecutionHandleRecord handle) {
        if (handle == null) {
            return;
        }

        OffsetDateTime updatedAt = handle.updatedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : handle.updatedAt();
        dsl.insertInto(TARGET_EXTERNAL_EXECUTION_HANDLES)
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.RUN_ID, runId)
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.TARGET_ID, targetId)
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.PROVIDER, handle.provider())
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_ID, nullableText(handle.executionId()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_NAME, nullableText(handle.executionName()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_STATUS, nullableText(handle.executionStatus()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_URL, nullableText(handle.executionUrl()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.LOGS_URL, nullableText(handle.logsUrl()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.UPDATED_AT, updatedAt)
            .onConflict(TARGET_EXTERNAL_EXECUTION_HANDLES.RUN_ID, TARGET_EXTERNAL_EXECUTION_HANDLES.TARGET_ID)
            .doUpdate()
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.PROVIDER, handle.provider())
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_ID, nullableText(handle.executionId()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_NAME, nullableText(handle.executionName()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_STATUS, nullableText(handle.executionStatus()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_URL, nullableText(handle.executionUrl()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.LOGS_URL, nullableText(handle.logsUrl()))
            .set(TARGET_EXTERNAL_EXECUTION_HANDLES.UPDATED_AT, updatedAt)
            .execute();
        touchRun(runId, updatedAt);
    }

    public List<String> requeueFailedTargets(String runId, String message) {
        return requeueTargets(runId, List.of(MappoTargetStage.FAILED), message);
    }

    public List<String> requeueActiveTargets(String runId, String message) {
        return requeueTargets(
            runId,
            List.of(MappoTargetStage.VALIDATING, MappoTargetStage.DEPLOYING, MappoTargetStage.VERIFYING),
            message
        );
    }

    private List<String> requeueTargets(String runId, Collection<MappoTargetStage> fromStatuses, String message) {
        List<String> targetIds = runExecutionStateRepository.listTargetIdsByStatuses(runId, fromStatuses);
        if (targetIds.isEmpty()) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (String targetId : targetIds) {
            dsl.update(TARGET_EXECUTION_RECORDS)
                .set(TARGET_EXECUTION_RECORDS.STATUS, MappoTargetStage.QUEUED)
                .set(TARGET_EXECUTION_RECORDS.ATTEMPT, TARGET_EXECUTION_RECORDS.ATTEMPT.plus(1))
                .set(TARGET_EXECUTION_RECORDS.UPDATED_AT, now)
                .where(TARGET_EXECUTION_RECORDS.RUN_ID.eq(runId))
                .and(TARGET_EXECUTION_RECORDS.TARGET_ID.eq(targetId))
                .execute();

            dsl.insertInto(TARGET_STAGE_RECORDS)
                .set(TARGET_STAGE_RECORDS.RUN_ID, runId)
                .set(TARGET_STAGE_RECORDS.TARGET_ID, targetId)
                .set(TARGET_STAGE_RECORDS.POSITION, nextStagePosition(runId, targetId))
                .set(TARGET_STAGE_RECORDS.STAGE, MappoTargetStage.QUEUED)
                .set(TARGET_STAGE_RECORDS.STARTED_AT, now)
                .set(TARGET_STAGE_RECORDS.ENDED_AT, now)
                .set(TARGET_STAGE_RECORDS.MESSAGE, defaultIfBlank(message, "Queued for retry."))
                .set(TARGET_STAGE_RECORDS.CORRELATION_ID, "corr-" + runId)
                .set(TARGET_STAGE_RECORDS.PORTAL_LINK, "")
                .execute();

            dsl.insertInto(TARGET_LOG_EVENTS)
                .set(TARGET_LOG_EVENTS.RUN_ID, runId)
                .set(TARGET_LOG_EVENTS.TARGET_ID, targetId)
                .set(TARGET_LOG_EVENTS.POSITION, nextLogPosition(runId, targetId))
                .set(TARGET_LOG_EVENTS.EVENT_TIMESTAMP, now)
                .set(TARGET_LOG_EVENTS.LEVEL, MappoForwarderLogLevel.info)
                .set(TARGET_LOG_EVENTS.STAGE, MappoTargetStage.QUEUED)
                .set(TARGET_LOG_EVENTS.MESSAGE, defaultIfBlank(message, "Queued for retry."))
                .set(TARGET_LOG_EVENTS.CORRELATION_ID, "corr-" + runId)
                .execute();
        }
        touchRun(runId, now);
        return targetIds;
    }

    private int nextStagePosition(String runId, String targetId) {
        Integer current = dsl.select(DSL.max(TARGET_STAGE_RECORDS.POSITION))
            .from(TARGET_STAGE_RECORDS)
            .where(TARGET_STAGE_RECORDS.RUN_ID.eq(runId))
            .and(TARGET_STAGE_RECORDS.TARGET_ID.eq(targetId))
            .fetchOne(0, Integer.class);
        return current == null ? 0 : current + 1;
    }

    private int nextLogPosition(String runId, String targetId) {
        Integer current = dsl.select(DSL.max(TARGET_LOG_EVENTS.POSITION))
            .from(TARGET_LOG_EVENTS)
            .where(TARGET_LOG_EVENTS.RUN_ID.eq(runId))
            .and(TARGET_LOG_EVENTS.TARGET_ID.eq(targetId))
            .fetchOne(0, Integer.class);
        return current == null ? 0 : current + 1;
    }

    private void touchRun(String runId, OffsetDateTime now) {
        dsl.update(RUNS)
            .set(RUNS.UPDATED_AT, now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now)
            .where(RUNS.ID.eq(runId))
            .execute();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private <T> T enumOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
