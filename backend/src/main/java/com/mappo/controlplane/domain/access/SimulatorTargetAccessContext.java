package com.mappo.controlplane.domain.access;

public record SimulatorTargetAccessContext(String reason) implements ResolvedTargetAccessContext {

    public static SimulatorTargetAccessContext defaults() {
        return new SimulatorTargetAccessContext("local_simulation");
    }
}
