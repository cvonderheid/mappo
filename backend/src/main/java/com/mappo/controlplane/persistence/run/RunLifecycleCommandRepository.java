package com.mappo.controlplane.persistence.run;

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
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.model.command.RunStopPolicyCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunLifecycleCommandRepository {

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
            .set(RUNS.PROJECT_ID, requiredText(request.projectId(), "project_id"))
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

    public void touchRun(String runId) {
        touchRun(runId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public void touchRun(String runId, OffsetDateTime now) {
        dsl.update(RUNS)
            .set(RUNS.UPDATED_AT, now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now)
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

    private String requiredText(Object value, String field) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private <T> T enumOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
