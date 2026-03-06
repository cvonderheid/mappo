package com.mappo.controlplane.repository;

import com.mappo.controlplane.util.JsonUtil;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

@Repository
public class RunRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public RunRepository(DSLContext dsl, JsonUtil jsonUtil) {
        this.dsl = dsl;
        this.jsonUtil = jsonUtil;
    }

    public List<Map<String, Object>> listRunSummaries() {
        Result<Record> rows = dsl.fetch(
            "select r.id, r.release_id, r.execution_mode::text as execution_mode, r.status::text as status, "
                + "r.strategy_mode::text as strategy_mode, r.created_at, r.started_at, r.ended_at, "
                + "r.subscription_concurrency, r.halt_reason "
                + "from runs r order by r.created_at desc"
        );

        Map<String, Map<String, Integer>> counts = loadRunCounts(rows.stream().map(r -> r.get("id", String.class)).toList());
        Map<String, List<String>> warnings = loadGuardrails(rows.stream().map(r -> r.get("id", String.class)).toList());

        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Record row : rows) {
            String runId = row.get("id", String.class);
            Map<String, Integer> perStatus = counts.getOrDefault(runId, Map.of());
            int succeeded = perStatus.getOrDefault("SUCCEEDED", 0);
            int failed = perStatus.getOrDefault("FAILED", 0);
            int queued = perStatus.getOrDefault("QUEUED", 0);
            int inProgress = perStatus.getOrDefault("VALIDATING", 0)
                + perStatus.getOrDefault("DEPLOYING", 0)
                + perStatus.getOrDefault("VERIFYING", 0);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", runId);
            summary.put("release_id", row.get("release_id", String.class));
            summary.put("execution_mode", row.get("execution_mode", String.class));
            summary.put("status", row.get("status", String.class));
            summary.put("strategy_mode", row.get("strategy_mode", String.class));
            summary.put("created_at", row.get("created_at", OffsetDateTime.class));
            summary.put("started_at", row.get("started_at", OffsetDateTime.class));
            summary.put("ended_at", row.get("ended_at", OffsetDateTime.class));
            summary.put("subscription_concurrency", row.get("subscription_concurrency", Integer.class));
            summary.put("total_targets", succeeded + failed + queued + inProgress);
            summary.put("succeeded_targets", succeeded);
            summary.put("failed_targets", failed);
            summary.put("in_progress_targets", inProgress);
            summary.put("queued_targets", queued);
            summary.put("halt_reason", row.get("halt_reason", String.class));
            summary.put("guardrail_warnings", warnings.getOrDefault(runId, List.of()));
            summaries.add(summary);
        }
        return summaries;
    }

    public Map<String, Object> getRunDetail(String runId) {
        Result<Record> rows = dsl.fetch(
            "select r.id, r.release_id, r.execution_mode::text as execution_mode, r.status::text as status, "
                + "r.strategy_mode::text as strategy_mode, r.wave_tag, r.concurrency, r.subscription_concurrency, "
                + "r.stop_policy_max_failure_count, r.stop_policy_max_failure_rate, "
                + "r.created_at, r.started_at, r.ended_at, r.updated_at, r.halt_reason "
                + "from runs r where r.id = ?",
            runId
        );
        if (rows.isEmpty()) {
            return Map.of();
        }

        Record row = rows.getFirst();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", runId);
        detail.put("release_id", row.get("release_id", String.class));
        detail.put("execution_mode", row.get("execution_mode", String.class));
        detail.put("status", row.get("status", String.class));
        detail.put("strategy_mode", row.get("strategy_mode", String.class));
        detail.put("wave_tag", row.get("wave_tag", String.class));
        detail.put("wave_order", loadWaveOrder(runId));
        detail.put("concurrency", row.get("concurrency", Integer.class));
        detail.put("subscription_concurrency", row.get("subscription_concurrency", Integer.class));
        Map<String, Object> stopPolicy = new LinkedHashMap<>();
        stopPolicy.put("max_failure_count", row.get("stop_policy_max_failure_count", Integer.class));
        stopPolicy.put("max_failure_rate", row.get("stop_policy_max_failure_rate", Double.class));
        detail.put("stop_policy", stopPolicy);
        detail.put("created_at", row.get("created_at", OffsetDateTime.class));
        detail.put("started_at", row.get("started_at", OffsetDateTime.class));
        detail.put("ended_at", row.get("ended_at", OffsetDateTime.class));
        detail.put("updated_at", row.get("updated_at", OffsetDateTime.class));
        detail.put("halt_reason", row.get("halt_reason", String.class));
        detail.put("guardrail_warnings", loadGuardrails(List.of(runId)).getOrDefault(runId, List.of()));
        detail.put("target_records", loadTargetRecords(runId));
        return detail;
    }

    public void createRun(
        String runId,
        Map<String, Object> request,
        List<Map<String, Object>> targets,
        String executionMode,
        boolean immediateSuccess
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String status = immediateSuccess ? "succeeded" : "running";
        dsl.query(
            "insert into runs (id, release_id, execution_mode, strategy_mode, wave_tag, concurrency, subscription_concurrency, "
                + "stop_policy_max_failure_count, stop_policy_max_failure_rate, status, halt_reason, "
                + "created_at, started_at, ended_at, updated_at) "
                + "values (?, ?, ?::mappo_deployment_mode, ?::mappo_strategy_mode, ?, ?, ?, ?, ?, ?::mappo_run_status, ?, ?, ?, ?, ?)",
            runId,
            request.get("release_id"),
            executionMode,
            request.getOrDefault("strategy_mode", "all_at_once"),
            request.getOrDefault("wave_tag", "ring"),
            request.getOrDefault("concurrency", 3),
            1,
            ((Map<?, ?>) request.getOrDefault("stop_policy", Map.of())).get("max_failure_count"),
            ((Map<?, ?>) request.getOrDefault("stop_policy", Map.of())).get("max_failure_rate"),
            status,
            null,
            now,
            now,
            immediateSuccess ? now : null,
            now
        ).execute();

        List<String> waveOrder = new ArrayList<>();
        Object waveOrderObj = request.get("wave_order");
        if (waveOrderObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                String value = String.valueOf(item).trim();
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
            dsl.query(
                "insert into run_wave_order (run_id, position, wave_value) values (?, ?, ?)",
                runId,
                i,
                waveOrder.get(i)
            ).execute();
        }

        for (int i = 0; i < targets.size(); i++) {
            Map<String, Object> target = targets.get(i);
            String targetId = String.valueOf(target.get("id"));
            dsl.query(
                "insert into run_targets (run_id, position, target_id) values (?, ?, ?)",
                runId,
                i,
                targetId
            ).execute();

            String stage = immediateSuccess ? "SUCCEEDED" : "QUEUED";
            dsl.query(
                "insert into target_execution_records (run_id, target_id, subscription_id, tenant_id, status, attempt, updated_at) "
                    + "values (?, ?, ?::uuid, ?::uuid, ?::mappo_target_stage, ?, ?)",
                runId,
                targetId,
                target.get("subscription_id"),
                target.get("tenant_id"),
                stage,
                1,
                now
            ).execute();

            dsl.query(
                "insert into target_stage_records (run_id, target_id, position, stage, started_at, ended_at, message, "
                    + "error_code, error_message, error_details, correlation_id, portal_link) "
                    + "values (?, ?, 0, ?::mappo_target_stage, ?, ?, ?, null, null, null, ?, ?)",
                runId,
                targetId,
                stage,
                now,
                immediateSuccess ? now : null,
                immediateSuccess ? "Succeeded." : "Queued.",
                "corr-" + runId,
                ""
            ).execute();

            dsl.query(
                "insert into target_log_events (run_id, target_id, position, event_timestamp, level, stage, message, correlation_id) "
                    + "values (?, ?, 0, ?, ?::mappo_forwarder_log_level, ?::mappo_target_stage, ?, ?)",
                runId,
                targetId,
                now,
                "info",
                stage,
                immediateSuccess ? "Deploy succeeded." : "Queued.",
                "corr-" + runId
            ).execute();
        }
    }

    public void markRunComplete(String runId, String status, String haltReason) {
        dsl.query(
            "update runs set status = ?::mappo_run_status, halt_reason = ?, ended_at = ?, updated_at = ? where id = ?",
            status,
            haltReason,
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC),
            runId
        ).execute();
    }

    public void deleteRunWarnings(String runId) {
        dsl.query("delete from run_guardrail_warnings where run_id = ?", runId).execute();
    }

    public void addRunWarning(String runId, int position, String warning) {
        dsl.query(
            "insert into run_guardrail_warnings (run_id, position, warning) values (?, ?, ?)",
            runId,
            position,
            warning
        ).execute();
    }

    private List<String> loadWaveOrder(String runId) {
        Result<Record> rows = dsl.fetch(
            "select wave_value from run_wave_order where run_id = ? order by position asc",
            runId
        );
        return rows.stream().map(row -> row.get("wave_value", String.class)).toList();
    }

    private Map<String, List<String>> loadGuardrails(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = runIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Result<Record> rows = dsl.fetch(
            "select run_id, warning from run_guardrail_warnings where run_id in (" + placeholders + ") order by position asc",
            runIds.toArray()
        );
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Record row : rows) {
            map.computeIfAbsent(row.get("run_id", String.class), key -> new ArrayList<>())
                .add(row.get("warning", String.class));
        }
        return map;
    }

    private Map<String, Map<String, Integer>> loadRunCounts(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = runIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Result<Record> rows = dsl.fetch(
            "select run_id, status::text as status, count(*) as cnt from target_execution_records "
                + "where run_id in (" + placeholders + ") group by run_id, status",
            runIds.toArray()
        );
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (Record row : rows) {
            counts.computeIfAbsent(row.get("run_id", String.class), key -> new LinkedHashMap<>())
                .put(row.get("status", String.class), row.get("cnt", Integer.class));
        }
        return counts;
    }

    private List<Map<String, Object>> loadTargetRecords(String runId) {
        Result<Record> rows = dsl.fetch(
            "select target_id, subscription_id::text as subscription_id, tenant_id::text as tenant_id, "
                + "status::text as status, attempt, updated_at "
                + "from target_execution_records where run_id = ? order by target_id asc",
            runId
        );

        Map<String, List<Map<String, Object>>> stages = loadStages(runId);
        Map<String, List<Map<String, Object>>> logs = loadLogs(runId);

        List<Map<String, Object>> targetRecords = new ArrayList<>();
        for (Record row : rows) {
            String targetId = row.get("target_id", String.class);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("target_id", targetId);
            target.put("subscription_id", row.get("subscription_id", String.class));
            target.put("tenant_id", row.get("tenant_id", String.class));
            target.put("status", row.get("status", String.class));
            target.put("attempt", row.get("attempt", Integer.class));
            target.put("updated_at", row.get("updated_at", OffsetDateTime.class));
            target.put("stages", stages.getOrDefault(targetId, List.of()));
            target.put("logs", logs.getOrDefault(targetId, List.of()));
            targetRecords.add(target);
        }
        return targetRecords;
    }

    private Map<String, List<Map<String, Object>>> loadStages(String runId) {
        Result<Record> rows = dsl.fetch(
            "select target_id, stage::text as stage, started_at, ended_at, message, "
                + "error_code, error_message, error_details::text as error_details, correlation_id, portal_link "
                + "from target_stage_records where run_id = ? order by position asc",
            runId
        );

        Map<String, List<Map<String, Object>>> stages = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get("target_id", String.class);
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("stage", row.get("stage", String.class));
            stage.put("started_at", row.get("started_at", OffsetDateTime.class));
            stage.put("ended_at", row.get("ended_at", OffsetDateTime.class));
            stage.put("message", row.get("message", String.class));

            String errorCode = row.get("error_code", String.class);
            String errorMessage = row.get("error_message", String.class);
            if (errorCode != null && errorMessage != null) {
                stage.put("error", Map.of(
                    "code", errorCode,
                    "message", errorMessage,
                    "details", jsonUtil.readMap(row.get("error_details", String.class))
                ));
            } else {
                stage.put("error", null);
            }

            stage.put("correlation_id", row.get("correlation_id", String.class));
            stage.put("portal_link", row.get("portal_link", String.class));
            stages.computeIfAbsent(targetId, key -> new ArrayList<>()).add(stage);
        }
        return stages;
    }

    private Map<String, List<Map<String, Object>>> loadLogs(String runId) {
        Result<Record> rows = dsl.fetch(
            "select target_id, event_timestamp, level::text as level, stage::text as stage, message, correlation_id "
                + "from target_log_events where run_id = ? order by position asc",
            runId
        );
        Map<String, List<Map<String, Object>>> logs = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get("target_id", String.class);
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("timestamp", row.get("event_timestamp", OffsetDateTime.class));
            log.put("level", row.get("level", String.class));
            log.put("stage", row.get("stage", String.class));
            log.put("message", row.get("message", String.class));
            log.put("correlation_id", row.get("correlation_id", String.class));
            logs.computeIfAbsent(targetId, key -> new ArrayList<>()).add(log);
        }
        return logs;
    }
}
