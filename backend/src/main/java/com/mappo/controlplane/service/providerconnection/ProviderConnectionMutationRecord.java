package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;

public record ProviderConnectionMutationRecord(
    String id,
    String name,
    ProviderConnectionProviderType provider,
    boolean enabled,
    String organizationFilter,
    String personalAccessTokenRef
) {
}
