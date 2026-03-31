package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderConnectionVerifyRequest(
    String id,
    ProviderConnectionProviderType provider,
    String organizationUrl,
    String personalAccessTokenRef
) {
}
