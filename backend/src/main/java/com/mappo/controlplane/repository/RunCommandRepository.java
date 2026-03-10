package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RUNS;
import static com.mappo.controlplane.jooq.Tables.RUN_GUARDRAIL_WARNINGS;
import static com.mappo.controlplane.jooq.Tables.RUN_TARGETS;
import static com.mappo.controlplane.jooq.Tables.RUN_WAVE_ORDER;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_RECORDS;
import static com.mappo.controlplane.jooq.Tables.TARGET_LOG_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGET_STAGE_RECORDS;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoStrategyMode;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.RunExecutionCountsRecord;
import com.mappo.controlplane.model.RunTargetRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetLogEventRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.TargetStageRecord;
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.model.command.RunStopPolicyCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunCommandRepository {

    private final DSLContext dsl;

    public void createRun(
        String runId,
        CreateRunCommand request,
        List<TargetRecord> targets,
        MappoReleaseSourceType executionSourceType
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RunStopPolicyCommand stopPolicy = request.stopPolicy();

        dsl.insertInto(RUNS)
            .set(RUNS.ID, runId)
            .set(RUNS.RELEASE_ID, normalize(request.releaseId()))
            .set(
                RUNS.EXECUTION_SOURCE_TYPE,
                enumOrDefault(executionSourceType, MappoReleaseSourceType.template_spec)
            )
            .set(RUNS.STRATEGY_MODE, enumOrDefault(request.strategyMode(), MappoStrategyMode.all_at_once))
            .set(RUNS.WAVE_TAG, defaultIfBlank(normalize(request.waveTag()), "ring"))
            .set(RUNS.CONCURRENCY, toInt(request.concurrency(), 3))
            .set(RUNS.SUBSCRIPTION_CONCURRENCY, 1)
            .set(RUNS.STOP_POLICY_MAX_FAILURE_COUNT, stopPolicy == null ? null : stopPolicy.maxFailureCount())
            .set(RUNS.STOP_POLICY_MAX_FAILURE_RATE, stopPolicy == null ? null : stopPolicy.maxFailureRate())
            .set(RUNS.STATUS, MappoRunStatus.running)
            .set(RUNS.HALT_REASON, (String) null)
            .set(RUNS.CREATED_AT, now)
            .set(RUNS.STARTED_AT, now)
            .set(RUNS.ENDED_AT, (OffsetDateTime) null)
            .set(RUNS.UPDATED_AT, now)
            .execute();

        List<String> waveOrder = new ArrayList<>(request.waveOrder());
        if (waveOrder.isEmpty()) {
            waveOrder.add("canary");
            waveOrder.add("prod");
        }

        for (int i = 0; i < waveOrder.size(); i++) {
            dsl.insertInto(RUN_WAVE_ORDER)
                .set(RUN_WAVE_ORDER.RUN_ID, runId)
                .set(RUN_WAVE_ORDER.POSITION, i)
                .set(RUN_WAVE_ORDER.WAVE_VALUE, waveOrder.get(i))
                .execute();
        }

        for (int i = 0; i < targets.size(); i++) {
            TargetRecord target = targets.get(i);
            String targetId = target.id();

            dsl.insertInto(RUN_TARGETS)
                .set(RUN_TARGETS.RUN_ID, runId)
                .set(RUN_TARGETS.POSITION, i)
                .set(RUN_TARGETS.TARGET_ID, targetId)
                .execute();

            MappoTargetStage stage = MappoTargetStage.QUEUED;

            dsl.insertInto(TARGET_EXECUTION_RECORDS)
                .set(TARGET_EXECUTION_RECORDS.RUN_ID, runId)
                .set(TARGET_EXECUTION_RECORDS.TARGET_ID, targetId)
                .set(TARGET_EXECUTION_RECORDS.SUBSCRIPTION_ID, requiredUuid(target.subscriptionId(), "subscription_id"))
                .set(TARGET_EXECUTION_RECORDS.TENANT_ID, requiredUuid(target.tenantId(), "tenant_id"))
                .set(TARGET_EXECUTION_RECORDS.STATUS, stage)
                .set(TARGET_EXECUTION_RECORDS.ATTEMPT, 1)
                .set(TARGET_EXECUTION_RECORDS.UPDATED_AT, now)
                .execute();

            dsl.insertInto(TARGET_STAGE_RECORDS)
                .set(TARGET_STAGE_RECORDS.RUN_ID, runId)
                .set(TARGET_STAGE_RECORDS.TARGET_ID, targetId)
                .set(TARGET_STAGE_RECORDS.POSITION, 0)
                .set(TARGET_STAGE_RECORDS.STAGE, stage)
                .set(TARGET_STAGE_RECORDS.STARTED_AT, now)
                .set(TARGET_STAGE_RECORDS.ENDED_AT, now)
                .set(TARGET_STAGE_RECORDS.MESSAGE, "Queued.")
                .set(TARGET_STAGE_RECORDS.ERROR_CODE, (String) null)
                .set(TARGET_STAGE_RECORDS.ERROR_MESSAGE, (String) null)
                .set(TARGET_STAGE_RECORDS.ERROR_STATUS_CODE, (Integer) null)
                .set(TARGET_STAGE_RECORDS.ERROR_DETAIL_TEXT, (String) null)
                .set(TARGET_STAGE_RECORDS.ERROR_DESIRED_IMAGE, (String) null)
                .set(TARGET_STAGE_RECORDS.AZURE_ERROR_CODE, (String) null)
                .set(TARGET_STAGE_RECORDS.AZURE_ERROR_MESSAGE, (String) null)
                .set(TARGET_STAGE_RECORDS.AZURE_REQUEST_ID, (String) null)
                .set(TARGET_STAGE_RECORDS.AZURE_ARM_SERVICE_REQUEST_ID, (String) null)
                .set(TARGET_STAGE_RECORDS.AZURE_CORRELATION_ID, (String) null)
                .set(TARGET_STAGE_RECORDS.AZURE_DEPLOYMENT_NAME, (String) null)
                .set(TARGET_STAGE_RECORDS.AZURE_OPERATION_ID, (String) null)
                .set(TARGET_STAGE_RECORDS.AZURE_RESOURCE_ID, (String) null)
                .set(TARGET_STAGE_RECORDS.CORRELATION_ID, "corr-" + runId)
                .set(TARGET_STAGE_RECORDS.PORTAL_LINK, "")
                .execute();

            dsl.insertInto(TARGET_LOG_EVENTS)
                .set(TARGET_LOG_EVENTS.RUN_ID, runId)
                .set(TARGET_LOG_EVENTS.TARGET_ID, targetId)
                .set(TARGET_LOG_EVENTS.POSITION, 0)
                .set(TARGET_LOG_EVENTS.EVENT_TIMESTAMP, now)
                .set(TARGET_LOG_EVENTS.LEVEL, MappoForwarderLogLevel.info)
                .set(TARGET_LOG_EVENTS.STAGE, stage)
                .set(TARGET_LOG_EVENTS.MESSAGE, "Queued.")
                .set(TARGET_LOG_EVENTS.CORRELATION_ID, "corr-" + runId)
                .execute();
        }
    }

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

    public void markRunComplete(String runId, MappoRunStatus status, String haltReason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.update(RUNS)
            .set(RUNS.STATUS, enumOrDefault(status, MappoRunStatus.succeeded))
            .set(RUNS.HALT_REASON, haltReason)
            .set(RUNS.ENDED_AT, now)
            .set(RUNS.UPDATED_AT, now)
            .where(RUNS.ID.eq(runId))
            .execute();
    }

    public void markRunRunning(String runId, String haltReason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.update(RUNS)
            .set(RUNS.STATUS, MappoRunStatus.running)
            .set(RUNS.HALT_REASON, nullableText(haltReason))
            .set(RUNS.ENDED_AT, (OffsetDateTime) null)
            .set(RUNS.UPDATED_AT, now)
            .where(RUNS.ID.eq(runId))
            .execute();
    }

    public void deleteRunWarnings(String runId) {
        dsl.deleteFrom(RUN_GUARDRAIL_WARNINGS)
            .where(RUN_GUARDRAIL_WARNINGS.RUN_ID.eq(runId))
            .execute();
        touchRun(runId);
    }

    public void addRunWarning(String runId, int position, String warning) {
        dsl.insertInto(RUN_GUARDRAIL_WARNINGS)
            .set(RUN_GUARDRAIL_WARNINGS.RUN_ID, runId)
            .set(RUN_GUARDRAIL_WARNINGS.POSITION, position)
            .set(RUN_GUARDRAIL_WARNINGS.WARNING, warning)
            .execute();
        touchRun(runId);
    }

    public void appendRunWarning(String runId, String warning) {
        Integer current = dsl.select(DSL.max(RUN_GUARDRAIL_WARNINGS.POSITION))
            .from(RUN_GUARDRAIL_WARNINGS)
            .where(RUN_GUARDRAIL_WARNINGS.RUN_ID.eq(runId))
            .fetchOne(0, Integer.class);
        addRunWarning(runId, current == null ? 0 : current + 1, warning);
    }

    public RunExecutionCountsRecord getExecutionCounts(String runId) {
        Map<MappoTargetStage, Integer> counts = loadRunCounts(List.of(runId)).getOrDefault(runId, Map.of());
        int succeeded = counts.getOrDefault(MappoTargetStage.SUCCEEDED, 0);
        int failed = counts.getOrDefault(MappoTargetStage.FAILED, 0);
        int inProgress = counts.getOrDefault(MappoTargetStage.VALIDATING, 0)
            + counts.getOrDefault(MappoTargetStage.DEPLOYING, 0)
            + counts.getOrDefault(MappoTargetStage.VERIFYING, 0);
        int queued = counts.getOrDefault(MappoTargetStage.QUEUED, 0);
        int total = dsl.fetchCount(
            dsl.select(TARGET_EXECUTION_RECORDS.TARGET_ID)
                .from(TARGET_EXECUTION_RECORDS)
                .where(TARGET_EXECUTION_RECORDS.RUN_ID.eq(runId))
        );
        return new RunExecutionCountsRecord(total, succeeded, failed, inProgress, queued);
    }

    public List<String> listTargetIdsByStatuses(String runId, Collection<MappoTargetStage> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return dsl.select(TARGET_EXECUTION_RECORDS.TARGET_ID)
            .from(TARGET_EXECUTION_RECORDS)
            .where(TARGET_EXECUTION_RECORDS.RUN_ID.eq(runId))
            .and(TARGET_EXECUTION_RECORDS.STATUS.in(statuses))
            .orderBy(TARGET_EXECUTION_RECORDS.TARGET_ID.asc())
            .fetch(TARGET_EXECUTION_RECORDS.TARGET_ID);
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

    public List<String> listStaleRunningRunIds(OffsetDateTime cutoff) {
        return dsl.select(RUNS.ID)
            .from(RUNS)
            .where(RUNS.STATUS.eq(MappoRunStatus.running))
            .and(RUNS.UPDATED_AT.lt(cutoff))
            .orderBy(RUNS.UPDATED_AT.asc())
            .fetch(RUNS.ID);
    }

    public void touchRun(String runId) {
        touchRunInternal(runId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public void touchRun(String runId, OffsetDateTime now) {
        touchRunInternal(runId, now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now);
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

    private Map<String, Map<MappoTargetStage, Integer>> loadRunCounts(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }

        var countField = DSL.count().as("cnt");
        var rows = dsl.select(TARGET_EXECUTION_RECORDS.RUN_ID, TARGET_EXECUTION_RECORDS.STATUS, countField)
            .from(TARGET_EXECUTION_RECORDS)
            .where(TARGET_EXECUTION_RECORDS.RUN_ID.in(runIds))
            .groupBy(TARGET_EXECUTION_RECORDS.RUN_ID, TARGET_EXECUTION_RECORDS.STATUS)
            .fetch();

        Map<String, Map<MappoTargetStage, Integer>> counts = new LinkedHashMap<>();
        for (Record row : rows) {
            String runId = row.get(TARGET_EXECUTION_RECORDS.RUN_ID);
            MappoTargetStage status = row.get(TARGET_EXECUTION_RECORDS.STATUS);
            int count = row.get(countField).intValue();
            counts.computeIfAbsent(runId, ignored -> new LinkedHashMap<>())
                .put(status, count);
        }
        return counts;
    }

    private List<String> requeueTargets(String runId, Collection<MappoTargetStage> fromStatuses, String message) {
        List<String> targetIds = listTargetIdsByStatuses(runId, fromStatuses);
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
        touchRunInternal(runId, now);
        return targetIds;
    }

    private void touchRunInternal(String runId, OffsetDateTime now) {
        dsl.update(RUNS)
            .set(RUNS.UPDATED_AT, now)
            .where(RUNS.ID.eq(runId))
            .execute();
    }

    private UUID requiredUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = normalize(value);
        if (text.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(text);
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
