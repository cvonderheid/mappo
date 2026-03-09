package com.mappo.controlplane.model.query;

public record TargetPageQuery(
    Integer page,
    Integer size,
    String targetId,
    String customerName,
    String tenantId,
    String subscriptionId,
    String ring,
    String region,
    String tier,
    String version,
    String runtimeStatus,
    String lastDeploymentStatus
) {
}
