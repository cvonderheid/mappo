package com.mappo.controlplane.integrations.azure.access;

import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;

public record AzureWorkloadRbacTargetAccessContext(
    String tenantId,
    String subscriptionId,
    String authModel
) implements ResolvedTargetAccessContext {
}
