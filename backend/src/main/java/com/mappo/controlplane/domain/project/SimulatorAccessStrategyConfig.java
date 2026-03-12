package com.mappo.controlplane.domain.project;

public record SimulatorAccessStrategyConfig() implements ProjectAccessStrategyConfig {

    public static SimulatorAccessStrategyConfig defaults() {
        return new SimulatorAccessStrategyConfig();
    }
}
