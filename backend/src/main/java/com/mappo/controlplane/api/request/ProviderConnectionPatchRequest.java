package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderConnectionPatchRequest(
    String name,
    ProviderConnectionProviderType provider,
    Boolean enabled,
    String organizationUrl,
    String personalAccessTokenRef
) {
}
