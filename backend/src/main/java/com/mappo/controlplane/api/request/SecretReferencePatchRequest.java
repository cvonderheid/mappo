package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.secretreference.SecretReferenceModeType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SecretReferencePatchRequest(
    String name,
    SecretReferenceProviderType provider,
    SecretReferenceUsageType usage,
    SecretReferenceModeType mode,
    String backendRef
) {
}
