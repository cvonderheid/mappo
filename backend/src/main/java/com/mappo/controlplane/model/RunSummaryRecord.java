package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoStrategyMode;
import java.time.OffsetDateTime;
import java.util.List;

public record RunSummaryRecord(
    String id,
    String projectId,
    String releaseId,
    MappoReleaseSourceType executionSourceType,
    MappoRunStatus status,
    MappoStrategyMode strategyMode,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,
    Integer subscriptionConcurrency,
    Integer totalTargets,
    Integer succeededTargets,
    Integer failedTargets,
    Integer inProgressTargets,
    Integer queuedTargets,
    String haltReason,
    List<String> guardrailWarnings
) {
}
