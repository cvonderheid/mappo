package com.mappo.controlplane.model.command;

public record RunStopPolicyCommand(
    Integer maxFailureCount,
    Double maxFailureRate
) {
}
