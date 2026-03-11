package com.mappo.controlplane.model;

import java.util.UUID;

public record TargetRuntimeProbeContextRecord(
    String targetId,
    String projectId,
    UUID tenantId,
    UUID subscriptionId,
    String containerAppResourceId
) {
}
