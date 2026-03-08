package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import java.util.Map;

public record TargetRegistrationPatchCommand(
    String displayName,
    String customerName,
    String managedApplicationId,
    String managedResourceGroupId,
    String containerAppResourceId,
    String containerAppName,
    String registrationSource,
    String deploymentStackName,
    MappoRegistryAuthMode registryAuthMode,
    String registryServer,
    String registryUsername,
    String registryPasswordSecretName,
    String lastDeployedRelease,
    MappoHealthStatus healthStatus,
    Map<String, String> tags
) {
}
