package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RUNS;
import static com.mappo.controlplane.jooq.Tables.RUN_GUARDRAIL_WARNINGS;
import static com.mappo.controlplane.jooq.Tables.RUN_TARGETS;
import static com.mappo.controlplane.jooq.Tables.RUN_WAVE_ORDER;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_RECORDS;
import static com.mappo.controlplane.jooq.Tables.TARGET_LOG_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGET_STAGE_RECORDS;

import com.mappo.controlplane.jooq.enums.MappoDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoStrategyMode;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.util.JsonUtil;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public List<Map<String, Object>> listRunSummaries() {
        var rows = dsl.select(
                RUNS.ID,
                RUNS.RELEASE_ID,
                RUNS.EXECUTION_MODE,
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
        Map<String, Map<String, Integer>> counts = loadRunCounts(runIds);
        Map<String, List<String>> warnings = loadGuardrails(runIds);

        List<Map<String, Object>> summaries = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String runId = row.get(RUNS.ID);
            Map<String, Integer> perStatus = counts.getOrDefault(runId, Map.of());
            int succeeded = perStatus.getOrDefault("SUCCEEDED", 0);
            int failed = perStatus.getOrDefault("FAILED", 0);
            int queued = perStatus.getOrDefault("QUEUED", 0);
            int inProgress = perStatus.getOrDefault("VALIDATING", 0)
                + perStatus.getOrDefault("DEPLOYING", 0)
                + perStatus.getOrDefault("VERIFYING", 0);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", runId);
            summary.put("release_id", row.get(RUNS.RELEASE_ID));
            summary.put("execution_mode", literal(row.get(RUNS.EXECUTION_MODE)));
            summary.put("status", literal(row.get(RUNS.STATUS)));
            summary.put("strategy_mode", literal(row.get(RUNS.STRATEGY_MODE)));
            summary.put("created_at", row.get(RUNS.CREATED_AT));
            summary.put("started_at", row.get(RUNS.STARTED_AT));
            summary.put("ended_at", row.get(RUNS.ENDED_AT));
            summary.put("subscription_concurrency", row.get(RUNS.SUBSCRIPTION_CONCURRENCY));
            summary.put("total_targets", succeeded + failed + queued + inProgress);
            summary.put("succeeded_targets", succeeded);
            summary.put("failed_targets", failed);
            summary.put("in_progress_targets", inProgress);
            summary.put("queued_targets", queued);
            summary.put("halt_reason", row.get(RUNS.HALT_REASON));
            summary.put("guardrail_warnings", warnings.getOrDefault(runId, List.of()));
            summaries.add(summary);
        }
        return summaries;
    }

    public Map<String, Object> getRunDetail(String runId) {
        Record row = dsl.select(
                RUNS.ID,
                RUNS.RELEASE_ID,
                RUNS.EXECUTION_MODE,
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
            return Map.of();
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", runId);
        detail.put("release_id", row.get(RUNS.RELEASE_ID));
        detail.put("execution_mode", literal(row.get(RUNS.EXECUTION_MODE)));
        detail.put("status", literal(row.get(RUNS.STATUS)));
        detail.put("strategy_mode", literal(row.get(RUNS.STRATEGY_MODE)));
        detail.put("wave_tag", row.get(RUNS.WAVE_TAG));
        detail.put("wave_order", loadWaveOrder(runId));
        detail.put("concurrency", row.get(RUNS.CONCURRENCY));
        detail.put("subscription_concurrency", row.get(RUNS.SUBSCRIPTION_CONCURRENCY));

        Map<String, Object> stopPolicy = new LinkedHashMap<>();
        stopPolicy.put("max_failure_count", row.get(RUNS.STOP_POLICY_MAX_FAILURE_COUNT));
        stopPolicy.put("max_failure_rate", row.get(RUNS.STOP_POLICY_MAX_FAILURE_RATE));
        detail.put("stop_policy", stopPolicy);

        detail.put("created_at", row.get(RUNS.CREATED_AT));
        detail.put("started_at", row.get(RUNS.STARTED_AT));
        detail.put("ended_at", row.get(RUNS.ENDED_AT));
        detail.put("updated_at", row.get(RUNS.UPDATED_AT));
        detail.put("halt_reason", row.get(RUNS.HALT_REASON));
        detail.put("guardrail_warnings", loadGuardrails(List.of(runId)).getOrDefault(runId, List.of()));
        detail.put("target_records", loadTargetRecords(runId));
        return detail;
    }

    @SuppressWarnings("unchecked")
    public void createRun(
        String runId,
        Map<String, Object> request,
        List<Map<String, Object>> targets,
        String executionMode,
        boolean immediateSuccess
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> stopPolicy = request.get("stop_policy") instanceof Map<?, ?> raw
            ? (Map<String, Object>) raw
            : Map.of();

        dsl.insertInto(RUNS)
            .set(RUNS.ID, runId)
            .set(RUNS.RELEASE_ID, normalize(request.get("release_id")))
            .set(
                RUNS.EXECUTION_MODE,
                enumOrDefault(MappoDeploymentMode.lookupLiteral(normalize(executionMode)), MappoDeploymentMode.container_patch)
            )
            .set(
                RUNS.STRATEGY_MODE,
                enumOrDefault(
                    MappoStrategyMode.lookupLiteral(normalize(request.getOrDefault("strategy_mode", "all_at_once"))),
                    MappoStrategyMode.all_at_once
                )
            )
            .set(RUNS.WAVE_TAG, defaultIfBlank(normalize(request.get("wave_tag")), "ring"))
            .set(RUNS.CONCURRENCY, toInt(request.get("concurrency"), 3))
            .set(RUNS.SUBSCRIPTION_CONCURRENCY, 1)
            .set(RUNS.STOP_POLICY_MAX_FAILURE_COUNT, toNullableInt(stopPolicy.get("max_failure_count")))
            .set(RUNS.STOP_POLICY_MAX_FAILURE_RATE, toNullableDouble(stopPolicy.get("max_failure_rate")))
            .set(RUNS.STATUS, immediateSuccess ? MappoRunStatus.succeeded : MappoRunStatus.running)
            .set(RUNS.HALT_REASON, (String) null)
            .set(RUNS.CREATED_AT, now)
            .set(RUNS.STARTED_AT, now)
            .set(RUNS.ENDED_AT, immediateSuccess ? now : null)
            .set(RUNS.UPDATED_AT, now)
            .execute();

        List<String> waveOrder = new ArrayList<>();
        Object waveOrderObj = request.get("wave_order");
        if (waveOrderObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                String value = normalize(item);
                if (!value.isBlank()) {
                    waveOrder.add(value);
                }
            }
        }
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
            Map<String, Object> target = targets.get(i);
            String targetId = normalize(target.get("id"));

            dsl.insertInto(RUN_TARGETS)
                .set(RUN_TARGETS.RUN_ID, runId)
                .set(RUN_TARGETS.POSITION, i)
                .set(RUN_TARGETS.TARGET_ID, targetId)
                .execute();

            MappoTargetStage stage = immediateSuccess ? MappoTargetStage.SUCCEEDED : MappoTargetStage.QUEUED;

            dsl.insertInto(TARGET_EXECUTION_RECORDS)
                .set(TARGET_EXECUTION_RECORDS.RUN_ID, runId)
                .set(TARGET_EXECUTION_RECORDS.TARGET_ID, targetId)
                .set(
                    TARGET_EXECUTION_RECORDS.SUBSCRIPTION_ID,
                    requiredUuid(target.get("subscription_id"), "subscription_id")
                )
                .set(
                    TARGET_EXECUTION_RECORDS.TENANT_ID,
                    requiredUuid(target.get("tenant_id"), "tenant_id")
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
                .set(TARGET_STAGE_RECORDS.ENDED_AT, immediateSuccess ? now : null)
                .set(TARGET_STAGE_RECORDS.MESSAGE, immediateSuccess ? "Succeeded." : "Queued.")
                .set(TARGET_STAGE_RECORDS.ERROR_CODE, (String) null)
                .set(TARGET_STAGE_RECORDS.ERROR_MESSAGE, (String) null)
                .set(TARGET_STAGE_RECORDS.ERROR_DETAILS, (JSONB) null)
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
                .set(TARGET_LOG_EVENTS.MESSAGE, immediateSuccess ? "Deploy succeeded." : "Queued.")
                .set(TARGET_LOG_EVENTS.CORRELATION_ID, "corr-" + runId)
                .execute();
        }
    }

    public void markRunComplete(String runId, String status, String haltReason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.update(RUNS)
            .set(RUNS.STATUS, enumOrDefault(MappoRunStatus.lookupLiteral(normalize(status)), MappoRunStatus.succeeded))
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

    private Map<String, Map<String, Integer>> loadRunCounts(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }

        var countField = DSL.count().as("cnt");
        var rows = dsl.select(TARGET_EXECUTION_RECORDS.RUN_ID, TARGET_EXECUTION_RECORDS.STATUS, countField)
            .from(TARGET_EXECUTION_RECORDS)
            .where(TARGET_EXECUTION_RECORDS.RUN_ID.in(runIds))
            .groupBy(TARGET_EXECUTION_RECORDS.RUN_ID, TARGET_EXECUTION_RECORDS.STATUS)
            .fetch();

        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (Record row : rows) {
            String runId = row.get(TARGET_EXECUTION_RECORDS.RUN_ID);
            String status = literal(row.get(TARGET_EXECUTION_RECORDS.STATUS));
            int count = row.get(countField).intValue();
            counts.computeIfAbsent(runId, ignored -> new LinkedHashMap<>())
                .put(status, count);
        }
        return counts;
    }

    private List<Map<String, Object>> loadTargetRecords(String runId) {
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

        Map<String, List<Map<String, Object>>> stages = loadStages(runId);
        Map<String, List<Map<String, Object>>> logs = loadLogs(runId);

        List<Map<String, Object>> targetRecords = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGET_EXECUTION_RECORDS.TARGET_ID);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("target_id", targetId);
            target.put("subscription_id", uuidText(row.get(TARGET_EXECUTION_RECORDS.SUBSCRIPTION_ID)));
            target.put("tenant_id", uuidText(row.get(TARGET_EXECUTION_RECORDS.TENANT_ID)));
            target.put("status", literal(row.get(TARGET_EXECUTION_RECORDS.STATUS)));
            target.put("attempt", row.get(TARGET_EXECUTION_RECORDS.ATTEMPT));
            target.put("updated_at", row.get(TARGET_EXECUTION_RECORDS.UPDATED_AT));
            target.put("stages", stages.getOrDefault(targetId, List.of()));
            target.put("logs", logs.getOrDefault(targetId, List.of()));
            targetRecords.add(target);
        }
        return targetRecords;
    }

    private Map<String, List<Map<String, Object>>> loadStages(String runId) {
        var rows = dsl.select(
                TARGET_STAGE_RECORDS.TARGET_ID,
                TARGET_STAGE_RECORDS.STAGE,
                TARGET_STAGE_RECORDS.STARTED_AT,
                TARGET_STAGE_RECORDS.ENDED_AT,
                TARGET_STAGE_RECORDS.MESSAGE,
                TARGET_STAGE_RECORDS.ERROR_CODE,
                TARGET_STAGE_RECORDS.ERROR_MESSAGE,
                TARGET_STAGE_RECORDS.ERROR_DETAILS,
                TARGET_STAGE_RECORDS.CORRELATION_ID,
                TARGET_STAGE_RECORDS.PORTAL_LINK
            )
            .from(TARGET_STAGE_RECORDS)
            .where(TARGET_STAGE_RECORDS.RUN_ID.eq(runId))
            .orderBy(TARGET_STAGE_RECORDS.TARGET_ID.asc(), TARGET_STAGE_RECORDS.POSITION.asc())
            .fetch();

        Map<String, List<Map<String, Object>>> stages = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get(TARGET_STAGE_RECORDS.TARGET_ID);
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("stage", literal(row.get(TARGET_STAGE_RECORDS.STAGE)));
            stage.put("started_at", row.get(TARGET_STAGE_RECORDS.STARTED_AT));
            stage.put("ended_at", row.get(TARGET_STAGE_RECORDS.ENDED_AT));
            stage.put("message", row.get(TARGET_STAGE_RECORDS.MESSAGE));

            String errorCode = row.get(TARGET_STAGE_RECORDS.ERROR_CODE);
            String errorMessage = row.get(TARGET_STAGE_RECORDS.ERROR_MESSAGE);
            if (errorCode != null && errorMessage != null) {
                stage.put("error", Map.of(
                    "code", errorCode,
                    "message", errorMessage,
                    "details", parseJsonMap(row.get(TARGET_STAGE_RECORDS.ERROR_DETAILS))
                ));
            } else {
                stage.put("error", null);
            }

            stage.put("correlation_id", row.get(TARGET_STAGE_RECORDS.CORRELATION_ID));
            stage.put("portal_link", row.get(TARGET_STAGE_RECORDS.PORTAL_LINK));
            stages.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(stage);
        }
        return stages;
    }

    private Map<String, List<Map<String, Object>>> loadLogs(String runId) {
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

        Map<String, List<Map<String, Object>>> logs = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get(TARGET_LOG_EVENTS.TARGET_ID);
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("timestamp", row.get(TARGET_LOG_EVENTS.EVENT_TIMESTAMP));
            log.put("level", literal(row.get(TARGET_LOG_EVENTS.LEVEL)));
            log.put("stage", literal(row.get(TARGET_LOG_EVENTS.STAGE)));
            log.put("message", row.get(TARGET_LOG_EVENTS.MESSAGE));
            log.put("correlation_id", row.get(TARGET_LOG_EVENTS.CORRELATION_ID));
            logs.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(log);
        }
        return logs;
    }

    private Map<String, Object> parseJsonMap(JSONB json) {
        if (json == null) {
            return Map.of();
        }
        return jsonUtil.readMap(json.data());
    }

    private String literal(org.jooq.EnumType value) {
        return value == null ? null : value.getLiteral();
    }

    private String uuidText(UUID value) {
        return value == null ? null : value.toString();
    }

    private UUID requiredUuid(Object value, String field) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(text);
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

    private Integer toNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = normalize(value);
        if (text.isBlank()) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private Double toNullableDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = normalize(value);
        if (text.isBlank()) {
            return null;
        }
        return Double.parseDouble(text);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private <T> T enumOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
