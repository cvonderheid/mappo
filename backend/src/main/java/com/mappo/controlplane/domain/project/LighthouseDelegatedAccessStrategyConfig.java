package com.mappo.controlplane.domain.project;

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
