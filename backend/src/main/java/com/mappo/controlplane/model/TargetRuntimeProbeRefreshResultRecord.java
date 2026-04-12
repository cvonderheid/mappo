package com.mappo.controlplane.model;

public record TargetRuntimeProbeRefreshResultRecord(
    String projectId,
    int checkedCount,
    boolean inProgress
) {
}
