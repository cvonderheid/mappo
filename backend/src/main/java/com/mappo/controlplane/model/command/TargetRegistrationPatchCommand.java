package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import java.util.Map;

public record TargetRegistrationPatchCommand(
    String displayName,
    String customerName,
    String managedApplicationId,
    String managedResourceGroupId,
    String containerAppResourceId,
    String containerAppName,
    String registrationSource,
    String lastDeployedRelease,
    MappoHealthStatus healthStatus,
    Map<String, String> tags
) {
}
