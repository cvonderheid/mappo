package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TargetRegistrationRecord(
    String targetId,
    UUID tenantId,
    UUID subscriptionId,
    String managedApplicationId,
    String managedResourceGroupId,
    String containerAppResourceId,
    String displayName,
    String customerName,
    Map<String, String> tags,
    TargetRegistrationMetadataRecord metadata,
    String lastEventId,
    String lastDeployedRelease,
    MappoHealthStatus healthStatus,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
