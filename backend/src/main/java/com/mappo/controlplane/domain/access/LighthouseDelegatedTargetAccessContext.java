package com.mappo.controlplane.domain.access;

public record LighthouseDelegatedTargetAccessContext(
    String targetTenantId,
    String targetSubscriptionId,
    String managingTenantId,
    String managingPrincipalClientId,
    String azureServiceConnectionName
) implements ResolvedTargetAccessContext {
}
