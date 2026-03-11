package com.mappo.controlplane.model.query;

import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;

public record TargetPageQuery(
    Integer page,
    Integer size,
    String projectId,
    String targetId,
    String customerName,
    String tenantId,
    String subscriptionId,
    String ring,
    String region,
    String tier,
    String version,
    MappoRuntimeProbeStatus runtimeStatus,
    MappoTargetStage lastDeploymentStatus
) {
}
