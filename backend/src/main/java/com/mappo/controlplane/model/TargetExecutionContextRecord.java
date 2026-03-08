package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import java.util.Map;
import java.util.UUID;

public record TargetExecutionContextRecord(
    String targetId,
    UUID subscriptionId,
    UUID tenantId,
    String managedResourceGroupId,
    String containerAppResourceId,
    String deploymentStackName,
    MappoRegistryAuthMode registryAuthMode,
    String registryServer,
    String registryUsername,
    String registryPasswordSecretName,
    Map<String, String> tags,
    MappoSimulatedFailureMode simulatedFailureMode
) {
}
