package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RUNS;
import static com.mappo.controlplane.jooq.Tables.RUN_GUARDRAIL_WARNINGS;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_RECORDS;

import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.PageMetadataRecord;
import com.mappo.controlplane.model.RunSummaryPageRecord;
import com.mappo.controlplane.model.RunSummaryRecord;
import com.mappo.controlplane.model.query.RunPageQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunSummaryQueryRepository {

    private final DSLContext dsl;

    public List<RunSummaryRecord> listRunSummaries() {
        return listRunSummariesPage(new RunPageQuery(0, 500, null, null, null)).items();
    }

    public RunSummaryPageRecord listRunSummariesPage(RunPageQuery query) {
        int page = normalizePage(query == null ? null : query.page());
        int size = normalizeSize(query == null ? null : query.size());
        String runIdFilter = normalize(query == null ? null : query.runId());
        String releaseIdFilter = normalize(query == null ? null : query.releaseId());
        MappoRunStatus statusFilter = query == null ? null : query.status();

        org.jooq.Condition condition = DSL.trueCondition();
        if (!runIdFilter.isBlank()) {
            condition = condition.and(RUNS.ID.containsIgnoreCase(runIdFilter));
        }
        if (!releaseIdFilter.isBlank()) {
            condition = condition.and(RUNS.RELEASE_ID.containsIgnoreCase(releaseIdFilter));
        }
        if (statusFilter != null) {
            condition = condition.and(RUNS.STATUS.eq(statusFilter));
        }

        long totalItems = dsl.fetchCount(
            dsl.select(RUNS.ID)
                .from(RUNS)
                .where(condition)
        );
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

        var rows = dsl.select(
                RUNS.ID,
                RUNS.PROJECT_ID,
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
            .where(condition)
            .orderBy(RUNS.CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
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
                row.get(RUNS.PROJECT_ID),
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
        return new RunSummaryPageRecord(
            summaries,
            new PageMetadataRecord(page, size, totalItems, totalPages),
            countActiveRuns()
        );
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

    private int countActiveRuns() {
        return dsl.fetchCount(
            dsl.select(RUNS.ID)
                .from(RUNS)
                .where(RUNS.STATUS.eq(MappoRunStatus.running))
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int normalizePage(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private int normalizeSize(Integer value) {
        if (value == null || value < 1) {
            return 25;
        }
        return Math.min(value, 100);
    }
}
