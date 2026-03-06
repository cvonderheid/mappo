package com.mappo.controlplane.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarketplaceEventPayloadRecord(
    String displayName,
    String customerName,
    String managedApplicationId,
    String managedResourceGroupId,
    String containerAppResourceId,
    String containerAppName,
    String targetGroup,
    String region,
    String environment,
    String tier,
    String lastDeployedRelease,
    MappoHealthStatus healthStatus,
    String registrationSource,
    String marketplacePayloadId
) {
}
