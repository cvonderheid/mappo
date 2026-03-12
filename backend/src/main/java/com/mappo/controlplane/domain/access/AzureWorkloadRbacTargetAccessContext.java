package com.mappo.controlplane.domain.access;

public record AzureWorkloadRbacTargetAccessContext(
    String tenantId,
    String subscriptionId,
    String authModel
) implements ResolvedTargetAccessContext {
}
