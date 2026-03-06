package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import java.time.OffsetDateTime;

public record TargetLogEventRecord(
    OffsetDateTime timestamp,
    MappoForwarderLogLevel level,
    MappoTargetStage stage,
    String message,
    String correlationId
) {
}
