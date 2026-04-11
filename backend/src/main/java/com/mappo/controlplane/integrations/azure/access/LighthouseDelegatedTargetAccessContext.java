package com.mappo.controlplane.integrations.azure.access;

import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;

public record LighthouseDelegatedTargetAccessContext(
    String targetTenantId,
    String targetSubscriptionId,
    String managingTenantId,
    String managingPrincipalClientId,
    String azureServiceConnectionName
) implements ResolvedTargetAccessContext {
}
