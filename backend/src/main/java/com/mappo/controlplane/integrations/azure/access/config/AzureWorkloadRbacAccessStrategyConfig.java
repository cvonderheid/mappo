package com.mappo.controlplane.integrations.azure.access.config;

import com.mappo.controlplane.domain.project.ProjectAccessStrategyConfig;

public record AzureWorkloadRbacAccessStrategyConfig(
    String authModel,
    boolean requiresAzureCredential,
    boolean requiresTargetExecutionMetadata
) implements ProjectAccessStrategyConfig {

    public static AzureWorkloadRbacAccessStrategyConfig defaults() {
        return new AzureWorkloadRbacAccessStrategyConfig(
            "provider_service_principal",
            true,
            true
        );
    }
}
