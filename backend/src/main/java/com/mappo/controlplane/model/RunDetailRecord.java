package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoStrategyMode;
import java.time.OffsetDateTime;
import java.util.List;

public record RunDetailRecord(
    String id,
    String releaseId,
    MappoReleaseSourceType executionSourceType,
    MappoRunStatus status,
    MappoStrategyMode strategyMode,
    String waveTag,
    List<String> waveOrder,
    Integer concurrency,
    Integer subscriptionConcurrency,
    RunStopPolicyRecord stopPolicy,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,
    OffsetDateTime updatedAt,
    String haltReason,
    List<String> guardrailWarnings,
    List<RunTargetRecord> targetRecords
) {
}
