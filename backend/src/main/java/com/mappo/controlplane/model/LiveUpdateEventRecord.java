package com.mappo.controlplane.model;

import java.time.OffsetDateTime;

public record LiveUpdateEventRecord(
    String type,
    String subjectId,
    OffsetDateTime occurredAt
) {
}
