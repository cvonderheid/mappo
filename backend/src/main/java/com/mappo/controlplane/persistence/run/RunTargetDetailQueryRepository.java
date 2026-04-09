package com.mappo.controlplane.persistence.run;

import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_RECORDS;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXTERNAL_EXECUTION_HANDLES;
import static com.mappo.controlplane.jooq.Tables.TARGET_LOG_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGET_STAGE_RECORDS;

import com.mappo.controlplane.model.ExternalExecutionHandleRecord;
import com.mappo.controlplane.model.RunTargetRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetLogEventRecord;
import com.mappo.controlplane.model.TargetStageRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RunTargetDetailQueryRepository {

    private final DSLContext dsl;

    public List<RunTargetRecord> loadTargetRecords(String runId) {
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
        Map<String, ExternalExecutionHandleRecord> externalExecutionHandles = loadExternalExecutionHandles(runId);

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
                logs.getOrDefault(targetId, List.of()),
                externalExecutionHandles.get(targetId)
            ));
        }
        return targetRecords;
    }

    private Map<String, ExternalExecutionHandleRecord> loadExternalExecutionHandles(String runId) {
        var rows = dsl.select(
                TARGET_EXTERNAL_EXECUTION_HANDLES.TARGET_ID,
                TARGET_EXTERNAL_EXECUTION_HANDLES.PROVIDER,
                TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_ID,
                TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_NAME,
                TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_STATUS,
                TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_URL,
                TARGET_EXTERNAL_EXECUTION_HANDLES.LOGS_URL,
                TARGET_EXTERNAL_EXECUTION_HANDLES.UPDATED_AT
            )
            .from(TARGET_EXTERNAL_EXECUTION_HANDLES)
            .where(TARGET_EXTERNAL_EXECUTION_HANDLES.RUN_ID.eq(runId))
            .fetch();

        Map<String, ExternalExecutionHandleRecord> handles = new LinkedHashMap<>();
        for (Record row : rows) {
            handles.put(
                row.get(TARGET_EXTERNAL_EXECUTION_HANDLES.TARGET_ID),
                new ExternalExecutionHandleRecord(
                    row.get(TARGET_EXTERNAL_EXECUTION_HANDLES.PROVIDER),
                    nullableText(row.get(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_ID)),
                    nullableText(row.get(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_NAME)),
                    nullableText(row.get(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_STATUS)),
                    nullableText(row.get(TARGET_EXTERNAL_EXECUTION_HANDLES.EXECUTION_URL)),
                    nullableText(row.get(TARGET_EXTERNAL_EXECUTION_HANDLES.LOGS_URL)),
                    row.get(TARGET_EXTERNAL_EXECUTION_HANDLES.UPDATED_AT)
                )
            );
        }
        return handles;
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

    private String nullableText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? null : normalized;
    }
}
