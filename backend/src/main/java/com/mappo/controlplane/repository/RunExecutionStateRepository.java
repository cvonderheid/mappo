package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RUNS;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_RECORDS;

import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.RunExecutionCountsRecord;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunExecutionStateRepository {

    private final DSLContext dsl;

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

    public List<String> listStaleRunningRunIds(OffsetDateTime cutoff) {
        return dsl.select(RUNS.ID)
            .from(RUNS)
            .where(RUNS.STATUS.eq(MappoRunStatus.running))
            .and(RUNS.UPDATED_AT.lt(cutoff))
            .orderBy(RUNS.UPDATED_AT.asc())
            .fetch(RUNS.ID);
    }

    private Map<String, Map<MappoTargetStage, Integer>> loadRunCounts(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }

        var countField = DSL.count().as("cnt");
        var rows = dsl.select(
                TARGET_EXECUTION_RECORDS.RUN_ID,
                TARGET_EXECUTION_RECORDS.STATUS,
                countField)
            .from(TARGET_EXECUTION_RECORDS)
            .where(TARGET_EXECUTION_RECORDS.RUN_ID.in(runIds))
            .groupBy(TARGET_EXECUTION_RECORDS.RUN_ID, TARGET_EXECUTION_RECORDS.STATUS)
            .fetch();

        Map<String, Map<MappoTargetStage, Integer>> counts = new LinkedHashMap<>();
        for (var row : rows) {
            String runId = row.get(TARGET_EXECUTION_RECORDS.RUN_ID);
            MappoTargetStage status = row.get(TARGET_EXECUTION_RECORDS.STATUS);
            Integer count = row.get(countField, Integer.class);
            counts.computeIfAbsent(runId, ignored -> new LinkedHashMap<>())
                .put(status, count == null ? 0 : count);
        }
        return counts;
    }
}
