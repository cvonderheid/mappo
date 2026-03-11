package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TargetUpsertCommand(
    String id,
    String projectId,
    UUID tenantId,
    UUID subscriptionId,
    Map<String, String> tags,
    String lastDeployedRelease,
    MappoHealthStatus healthStatus,
    OffsetDateTime lastCheckInAt,
    MappoSimulatedFailureMode simulatedFailureMode
) {
    public TargetUpsertCommand {
        tags = tags == null ? Map.of() : tags;
    }
}
