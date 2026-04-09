package com.mappo.controlplane.persistence.run;

import static com.mappo.controlplane.jooq.Tables.RUNS;
import static com.mappo.controlplane.jooq.Tables.RUN_GUARDRAIL_WARNINGS;
import static com.mappo.controlplane.jooq.Tables.RUN_WAVE_ORDER;

import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunStopPolicyRecord;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunDetailQueryRepository {

    private final DSLContext dsl;
    private final RunTargetDetailQueryRepository runTargetDetailQueryRepository;

    public Optional<RunDetailRecord> getRunDetail(String runId) {
        Record row = dsl.select(
                RUNS.ID,
                RUNS.PROJECT_ID,
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
            row.get(RUNS.PROJECT_ID),
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
            loadGuardrails(runId),
            runTargetDetailQueryRepository.loadTargetRecords(runId)
        );

        return Optional.of(detail);
    }

    private List<String> loadWaveOrder(String runId) {
        return dsl.select(RUN_WAVE_ORDER.WAVE_VALUE)
            .from(RUN_WAVE_ORDER)
            .where(RUN_WAVE_ORDER.RUN_ID.eq(runId))
            .orderBy(RUN_WAVE_ORDER.POSITION.asc())
            .fetch(RUN_WAVE_ORDER.WAVE_VALUE);
    }

    private List<String> loadGuardrails(String runId) {
        return dsl.select(RUN_GUARDRAIL_WARNINGS.WARNING)
            .from(RUN_GUARDRAIL_WARNINGS)
            .where(RUN_GUARDRAIL_WARNINGS.RUN_ID.eq(runId))
            .orderBy(RUN_GUARDRAIL_WARNINGS.POSITION.asc())
            .fetch(RUN_GUARDRAIL_WARNINGS.WARNING);
    }
}
