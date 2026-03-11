package com.mappo.controlplane.model;

import java.time.OffsetDateTime;

public record LiveUpdateEventRecord(
    String type,
    String projectId,
    String subjectId,
    OffsetDateTime occurredAt
) {
}
