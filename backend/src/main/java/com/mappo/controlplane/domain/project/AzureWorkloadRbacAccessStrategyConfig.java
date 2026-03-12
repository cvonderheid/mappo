package com.mappo.controlplane.domain.project;

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
