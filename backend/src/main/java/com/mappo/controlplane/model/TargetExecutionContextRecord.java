package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import java.util.Map;
import java.util.UUID;

public record TargetExecutionContextRecord(
    String targetId,
    UUID subscriptionId,
    UUID tenantId,
    String managedResourceGroupId,
    String containerAppResourceId,
    Map<String, String> tags,
    MappoSimulatedFailureMode simulatedFailureMode
) {
}
