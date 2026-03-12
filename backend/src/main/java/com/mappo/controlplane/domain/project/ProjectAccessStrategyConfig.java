package com.mappo.controlplane.domain.project;

public sealed interface ProjectAccessStrategyConfig
    permits SimulatorAccessStrategyConfig, AzureWorkloadRbacAccessStrategyConfig, LighthouseDelegatedAccessStrategyConfig {
}
