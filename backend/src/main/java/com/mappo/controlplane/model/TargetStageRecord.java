package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import java.time.OffsetDateTime;

public record TargetStageRecord(
    MappoTargetStage stage,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,
    String message,
    StageErrorRecord error,
    String correlationId,
    String portalLink
) {
}
