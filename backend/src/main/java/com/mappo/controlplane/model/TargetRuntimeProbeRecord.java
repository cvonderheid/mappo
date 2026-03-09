package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import java.time.OffsetDateTime;

public record TargetRuntimeProbeRecord(
    String targetId,
    MappoRuntimeProbeStatus runtimeStatus,
    OffsetDateTime checkedAt,
    String endpointUrl,
    Integer httpStatusCode,
    String summary
) {
}
