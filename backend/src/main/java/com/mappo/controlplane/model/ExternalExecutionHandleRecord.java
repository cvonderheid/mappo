package com.mappo.controlplane.model;

import java.time.OffsetDateTime;

public record ExternalExecutionHandleRecord(
    String provider,
    String executionId,
    String executionName,
    String executionStatus,
    String executionUrl,
    String logsUrl,
    OffsetDateTime updatedAt
) {
}
