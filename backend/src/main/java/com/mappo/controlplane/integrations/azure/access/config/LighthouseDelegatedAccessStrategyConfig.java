package com.mappo.controlplane.integrations.azure.access.config;

import com.mappo.controlplane.domain.project.ProjectAccessStrategyConfig;

public record LighthouseDelegatedAccessStrategyConfig(
    String azureServiceConnectionName,
    String managingTenantId,
    String managingPrincipalClientId,
    boolean requiresDelegation
) implements ProjectAccessStrategyConfig {

    public static LighthouseDelegatedAccessStrategyConfig defaults() {
        return new LighthouseDelegatedAccessStrategyConfig(
            "",
            "",
            "",
            true
        );
    }
}
