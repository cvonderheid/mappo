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
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.model.command.RunStopPolicyCommand;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunStopPolicyRecord;
import com.mappo.controlplane.model.RunSummaryRecord;
import com.mappo.controlplane.model.RunTargetRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetLogEventRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.TargetStageRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunRepository {

    private final DSLContext dsl;

    public List<RunSummaryRecord> listRunSummaries() {
        var rows = dsl.select(
                RUNS.ID,
                RUNS.RELEASE_ID,
                RUNS.EXECUTION_SOURCE_TYPE,
                RUNS.STATUS,
                RUNS.STRATEGY_MODE,
                RUNS.CREATED_AT,
                RUNS.STARTED_AT,
                RUNS.ENDED_AT,
                RUNS.SUBSCRIPTION_CONCURRENCY,
                RUNS.HALT_REASON
            )
            .from(RUNS)
            .orderBy(RUNS.CREATED_AT.desc())
            .fetch();

        List<String> runIds = rows.stream().map(row -> row.get(RUNS.ID)).toList();
        Map<String, Map<MappoTargetStage, Integer>> counts = loadRunCounts(runIds);
        Map<String, List<String>> warnings = loadGuardrails(runIds);

        List<RunSummaryRecord> summaries = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String runId = row.get(RUNS.ID);
            Map<MappoTargetStage, Integer> perStatus = counts.getOrDefault(runId, Map.of());
            int succeeded = perStatus.getOrDefault(MappoTargetStage.SUCCEEDED, 0);
            int failed = perStatus.getOrDefault(MappoTargetStage.FAILED, 0);
            int queued = perStatus.getOrDefault(MappoTargetStage.QUEUED, 0);
            int inProgress = perStatus.getOrDefault(MappoTargetStage.VALIDATING, 0)
                + perStatus.getOrDefault(MappoTargetStage.DEPLOYING, 0)
                + perStatus.getOrDefault(MappoTargetStage.VERIFYING, 0);

            summaries.add(new RunSummaryRecord(
                runId,
                row.get(RUNS.RELEASE_ID),
                row.get(RUNS.EXECUTION_SOURCE_TYPE),
                row.get(RUNS.STATUS),
                row.get(RUNS.STRATEGY_MODE),
                row.get(RUNS.CREATED_AT),
                row.get(RUNS.STARTED_AT),
                row.get(RUNS.ENDED_AT),
                row.get(RUNS.SUBSCRIPTION_CONCURRENCY),
                succeeded + failed + queued + inProgress,
                succeeded,
                failed,
                inProgress,
                queued,
                row.get(RUNS.HALT_REASON),
                warnings.getOrDefault(runId, List.of())
            ));
        }
        return summaries;
    }

    public Optional<RunDetailRecord> getRunDetail(String runId) {
        Record row = dsl.select(
                RUNS.ID,
                RUNS.RELEASE_ID,
                RUNS.EXECUTION_SOURCE_TYPE,
                RUNS.STATUS,
                RUNS.STRATEGY_MODE,
                RUNS.WAVE_TAG,
                RUNS.CONCURRENCY,
                RUNS.SUBSCRIPTION_CONCURRENCY,
                RUNS.STOP_POLICY_MAX_FAILURE_COUNT,
                RUNS.STOP_POLICY_MAX_FAILURE_RATE,
                RUNS.CREATED_AT,
                RUNS.STARTED_AT,
                RUNS.ENDED_AT,
                RUNS.UPDATED_AT,
                RUNS.HALT_REASON
            )
            .from(RUNS)
            .where(RUNS.ID.eq(runId))
            .fetchOne();

        if (row == null) {
            return Optional.empty();
        }

        RunStopPolicyRecord stopPolicy = new RunStopPolicyRecord(
            row.get(RUNS.STOP_POLICY_MAX_FAILURE_COUNT),
            row.get(RUNS.STOP_POLICY_MAX_FAILURE_RATE)
        );

        RunDetailRecord detail = new RunDetailRecord(
            runId,
            row.get(RUNS.RELEASE_ID),
            row.get(RUNS.EXECUTION_SOURCE_TYPE),
            row.get(RUNS.STATUS),
            row.get(RUNS.STRATEGY_MODE),
            row.get(RUNS.WAVE_TAG),
            loadWaveOrder(runId),
            row.get(RUNS.CONCURRENCY),
            row.get(RUNS.SUBSCRIPTION_CONCURRENCY),
            stopPolicy,
            row.get(RUNS.CREATED_AT),
            row.get(RUNS.STARTED_AT),
            row.get(RUNS.ENDED_AT),
            row.get(RUNS.UPDATED_AT),
            row.get(RUNS.HALT_REASON),
            loadGuardrails(List.of(runId)).getOrDefault(runId, List.of()),
            loadTargetRecords(runId)
        );

        return Optional.of(detail);
    }

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
                .set(
                    TARGET_EXECUTION_RECORDS.SUBSCRIPTION_ID,
                    requiredUuid(target.subscriptionId(), "subscription_id")
                )
                .set(
                    TARGET_EXECUTION_RECORDS.TENANT_ID,
                    requiredUuid(target.tenantId(), "tenant_id")
                )
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
        dsl.update(TARGET_EXECUTION_RECORDS)
            .set(TARGET_EXECUTION_RECORDS.STATUS, enumOrDefault(status, MappoTargetStage.QUEUED))
            .set(TARGET_EXECUTION_RECORDS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(TARGET_EXECUTION_RECORDS.RUN_ID.eq(runId))
            .and(TARGET_EXECUTION_RECORDS.TARGET_ID.eq(targetId))
            .execute();
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

    public void deleteRunWarnings(String runId) {
        dsl.deleteFrom(RUN_GUARDRAIL_WARNINGS)
            .where(RUN_GUARDRAIL_WARNINGS.RUN_ID.eq(runId))
            .execute();
    }

    public void addRunWarning(String runId, int position, String warning) {
        dsl.insertInto(RUN_GUARDRAIL_WARNINGS)
            .set(RUN_GUARDRAIL_WARNINGS.RUN_ID, runId)
            .set(RUN_GUARDRAIL_WARNINGS.POSITION, position)
            .set(RUN_GUARDRAIL_WARNINGS.WARNING, warning)
            .execute();
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

    private List<String> loadWaveOrder(String runId) {
        return dsl.select(RUN_WAVE_ORDER.WAVE_VALUE)
            .from(RUN_WAVE_ORDER)
            .where(RUN_WAVE_ORDER.RUN_ID.eq(runId))
            .orderBy(RUN_WAVE_ORDER.POSITION.asc())
            .fetch(RUN_WAVE_ORDER.WAVE_VALUE);
    }

    private Map<String, List<String>> loadGuardrails(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }

        var rows = dsl.select(RUN_GUARDRAIL_WARNINGS.RUN_ID, RUN_GUARDRAIL_WARNINGS.WARNING)
            .from(RUN_GUARDRAIL_WARNINGS)
            .where(RUN_GUARDRAIL_WARNINGS.RUN_ID.in(runIds))
            .orderBy(RUN_GUARDRAIL_WARNINGS.POSITION.asc())
            .fetch();

        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Record row : rows) {
            map.computeIfAbsent(row.get(RUN_GUARDRAIL_WARNINGS.RUN_ID), ignored -> new ArrayList<>())
                .add(row.get(RUN_GUARDRAIL_WARNINGS.WARNING));
        }
        return map;
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

    private List<RunTargetRecord> loadTargetRecords(String runId) {
        var rows = dsl.select(
                TARGET_EXECUTION_RECORDS.TARGET_ID,
                TARGET_EXECUTION_RECORDS.SUBSCRIPTION_ID,
                TARGET_EXECUTION_RECORDS.TENANT_ID,
                TARGET_EXECUTION_RECORDS.STATUS,
                TARGET_EXECUTION_RECORDS.ATTEMPT,
                TARGET_EXECUTION_RECORDS.UPDATED_AT
            )
            .from(TARGET_EXECUTION_RECORDS)
            .where(TARGET_EXECUTION_RECORDS.RUN_ID.eq(runId))
            .orderBy(TARGET_EXECUTION_RECORDS.TARGET_ID.asc())
            .fetch();

        Map<String, List<TargetStageRecord>> stages = loadStages(runId);
        Map<String, List<TargetLogEventRecord>> logs = loadLogs(runId);

        List<RunTargetRecord> targetRecords = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGET_EXECUTION_RECORDS.TARGET_ID);
            targetRecords.add(new RunTargetRecord(
                targetId,
                row.get(TARGET_EXECUTION_RECORDS.SUBSCRIPTION_ID),
                row.get(TARGET_EXECUTION_RECORDS.TENANT_ID),
                row.get(TARGET_EXECUTION_RECORDS.STATUS),
                row.get(TARGET_EXECUTION_RECORDS.ATTEMPT),
                row.get(TARGET_EXECUTION_RECORDS.UPDATED_AT),
                stages.getOrDefault(targetId, List.of()),
                logs.getOrDefault(targetId, List.of())
            ));
        }
        return targetRecords;
    }

    private Map<String, List<TargetStageRecord>> loadStages(String runId) {
        var rows = dsl.select(
                TARGET_STAGE_RECORDS.TARGET_ID,
                TARGET_STAGE_RECORDS.STAGE,
                TARGET_STAGE_RECORDS.STARTED_AT,
                TARGET_STAGE_RECORDS.ENDED_AT,
                TARGET_STAGE_RECORDS.MESSAGE,
                TARGET_STAGE_RECORDS.ERROR_CODE,
                TARGET_STAGE_RECORDS.ERROR_MESSAGE,
                TARGET_STAGE_RECORDS.ERROR_STATUS_CODE,
                TARGET_STAGE_RECORDS.ERROR_DETAIL_TEXT,
                TARGET_STAGE_RECORDS.ERROR_DESIRED_IMAGE,
                TARGET_STAGE_RECORDS.AZURE_ERROR_CODE,
                TARGET_STAGE_RECORDS.AZURE_ERROR_MESSAGE,
                TARGET_STAGE_RECORDS.AZURE_REQUEST_ID,
                TARGET_STAGE_RECORDS.AZURE_ARM_SERVICE_REQUEST_ID,
                TARGET_STAGE_RECORDS.AZURE_CORRELATION_ID,
                TARGET_STAGE_RECORDS.AZURE_DEPLOYMENT_NAME,
                TARGET_STAGE_RECORDS.AZURE_OPERATION_ID,
                TARGET_STAGE_RECORDS.AZURE_RESOURCE_ID,
                TARGET_STAGE_RECORDS.CORRELATION_ID,
                TARGET_STAGE_RECORDS.PORTAL_LINK
            )
            .from(TARGET_STAGE_RECORDS)
            .where(TARGET_STAGE_RECORDS.RUN_ID.eq(runId))
            .orderBy(TARGET_STAGE_RECORDS.TARGET_ID.asc(), TARGET_STAGE_RECORDS.POSITION.asc())
            .fetch();

        Map<String, List<TargetStageRecord>> stages = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get(TARGET_STAGE_RECORDS.TARGET_ID);

            StageErrorRecord error = null;
            String errorCode = row.get(TARGET_STAGE_RECORDS.ERROR_CODE);
            String errorMessage = row.get(TARGET_STAGE_RECORDS.ERROR_MESSAGE);
            if (errorCode != null && errorMessage != null) {
                error = new StageErrorRecord(
                    errorCode,
                    errorMessage,
                    stageErrorDetails(row)
                );
            }

            TargetStageRecord stage = new TargetStageRecord(
                row.get(TARGET_STAGE_RECORDS.STAGE),
                row.get(TARGET_STAGE_RECORDS.STARTED_AT),
                row.get(TARGET_STAGE_RECORDS.ENDED_AT),
                row.get(TARGET_STAGE_RECORDS.MESSAGE),
                error,
                row.get(TARGET_STAGE_RECORDS.CORRELATION_ID),
                row.get(TARGET_STAGE_RECORDS.PORTAL_LINK)
            );
            stages.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(stage);
        }
        return stages;
    }

    private Map<String, List<TargetLogEventRecord>> loadLogs(String runId) {
        var rows = dsl.select(
                TARGET_LOG_EVENTS.TARGET_ID,
                TARGET_LOG_EVENTS.EVENT_TIMESTAMP,
                TARGET_LOG_EVENTS.LEVEL,
                TARGET_LOG_EVENTS.STAGE,
                TARGET_LOG_EVENTS.MESSAGE,
                TARGET_LOG_EVENTS.CORRELATION_ID
            )
            .from(TARGET_LOG_EVENTS)
            .where(TARGET_LOG_EVENTS.RUN_ID.eq(runId))
            .orderBy(TARGET_LOG_EVENTS.TARGET_ID.asc(), TARGET_LOG_EVENTS.POSITION.asc())
            .fetch();

        Map<String, List<TargetLogEventRecord>> logs = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get(TARGET_LOG_EVENTS.TARGET_ID);
            TargetLogEventRecord log = new TargetLogEventRecord(
                row.get(TARGET_LOG_EVENTS.EVENT_TIMESTAMP),
                row.get(TARGET_LOG_EVENTS.LEVEL),
                row.get(TARGET_LOG_EVENTS.STAGE),
                row.get(TARGET_LOG_EVENTS.MESSAGE),
                row.get(TARGET_LOG_EVENTS.CORRELATION_ID)
            );
            logs.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(log);
        }
        return logs;
    }

    private StageErrorDetailsRecord stageErrorDetails(Record row) {
        return new StageErrorDetailsRecord(
            row.get(TARGET_STAGE_RECORDS.ERROR_STATUS_CODE),
            nullableText(row.get(TARGET_STAGE_RECORDS.ERROR_DETAIL_TEXT)),
            nullableText(row.get(TARGET_STAGE_RECORDS.ERROR_DESIRED_IMAGE)),
            nullableText(row.get(TARGET_STAGE_RECORDS.AZURE_ERROR_CODE)),
            nullableText(row.get(TARGET_STAGE_RECORDS.AZURE_ERROR_MESSAGE)),
            nullableText(row.get(TARGET_STAGE_RECORDS.AZURE_REQUEST_ID)),
            nullableText(row.get(TARGET_STAGE_RECORDS.AZURE_ARM_SERVICE_REQUEST_ID)),
            nullableText(row.get(TARGET_STAGE_RECORDS.AZURE_CORRELATION_ID)),
            nullableText(row.get(TARGET_STAGE_RECORDS.AZURE_DEPLOYMENT_NAME)),
            nullableText(row.get(TARGET_STAGE_RECORDS.AZURE_OPERATION_ID)),
            nullableText(row.get(TARGET_STAGE_RECORDS.AZURE_RESOURCE_ID))
        );
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
