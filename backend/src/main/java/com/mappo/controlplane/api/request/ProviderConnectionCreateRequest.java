package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderConnectionCreateRequest(
    @NotBlank String name,
    @NotNull ProviderConnectionProviderType provider,
    Boolean enabled,
    String organizationUrl,
    String personalAccessTokenRef
) {
}
