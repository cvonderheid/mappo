package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import java.time.OffsetDateTime;
import java.util.Map;

public record TargetRegistrationUpsertCommand(
    String targetId,
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
    Map<String, String> executionConfig,
    String lastEventId,
    OffsetDateTime createdAt
) {
}
