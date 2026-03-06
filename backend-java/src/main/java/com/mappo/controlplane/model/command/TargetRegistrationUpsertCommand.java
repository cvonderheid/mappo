package com.mappo.controlplane.model.command;

import java.time.OffsetDateTime;

public record TargetRegistrationUpsertCommand(
    String targetId,
    String displayName,
    String customerName,
    String managedApplicationId,
    String managedResourceGroupId,
    String containerAppResourceId,
    String containerAppName,
    String registrationSource,
    String lastEventId,
    OffsetDateTime createdAt
) {
}
