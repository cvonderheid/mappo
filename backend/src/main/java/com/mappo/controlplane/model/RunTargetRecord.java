package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RunTargetRecord(
    String targetId,
    UUID subscriptionId,
    UUID tenantId,
    MappoTargetStage status,
    Integer attempt,
    OffsetDateTime updatedAt,
    List<TargetStageRecord> stages,
    List<TargetLogEventRecord> logs,
    ExternalExecutionHandleRecord externalExecutionHandle
) {
    public RunTargetRecord(
        String targetId,
        UUID subscriptionId,
        UUID tenantId,
        MappoTargetStage status,
        Integer attempt,
        OffsetDateTime updatedAt,
        List<TargetStageRecord> stages,
        List<TargetLogEventRecord> logs
    ) {
        this(targetId, subscriptionId, tenantId, status, attempt, updatedAt, stages, logs, null);
    }
}
