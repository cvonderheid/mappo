package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TargetRecord(
    String id,
    UUID tenantId,
    UUID subscriptionId,
    String managedAppId,
    String customerName,
    Map<String, String> tags,
    String lastDeployedRelease,
    MappoHealthStatus healthStatus,
    OffsetDateTime lastCheckInAt,
    MappoSimulatedFailureMode simulatedFailureMode
) {
}
