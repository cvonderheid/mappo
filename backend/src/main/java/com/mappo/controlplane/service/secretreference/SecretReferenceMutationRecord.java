package com.mappo.controlplane.service.secretreference;

import com.mappo.controlplane.domain.secretreference.SecretReferenceModeType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;

public record SecretReferenceMutationRecord(
    String id,
    String name,
    SecretReferenceProviderType provider,
    SecretReferenceUsageType usage,
    SecretReferenceModeType mode,
    String backendRef
) {
}
