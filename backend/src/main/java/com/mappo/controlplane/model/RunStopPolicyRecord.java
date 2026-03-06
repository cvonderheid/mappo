package com.mappo.controlplane.model;

public record RunStopPolicyRecord(
    Integer maxFailureCount,
    Double maxFailureRate
) {
}
