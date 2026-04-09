package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.secretreference.SecretReferenceModeType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SecretReferenceCreateRequest(
    @NotBlank String id,
    @NotBlank String name,
    @NotNull SecretReferenceProviderType provider,
    @NotNull SecretReferenceUsageType usage,
    @NotNull SecretReferenceModeType mode,
    String backendRef
) {
}
