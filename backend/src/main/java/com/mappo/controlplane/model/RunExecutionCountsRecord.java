package com.mappo.controlplane.model;

public record RunExecutionCountsRecord(
    int totalTargets,
    int succeededTargets,
    int failedTargets,
    int inProgressTargets,
    int queuedTargets
) {

    public int processedTargets() {
        return succeededTargets + failedTargets + inProgressTargets;
    }

    public boolean hasQueuedTargets() {
        return queuedTargets > 0;
    }

    public boolean hasActiveTargets() {
        return inProgressTargets > 0;
    }
}
