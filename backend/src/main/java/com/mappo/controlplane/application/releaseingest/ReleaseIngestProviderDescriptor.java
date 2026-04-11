package com.mappo.controlplane.application.releaseingest;

import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;

public interface ReleaseIngestProviderDescriptor {

    ReleaseIngestProviderType provider();

    String defaultSecretReference();

    SecretReferenceProviderType secretReferenceProvider();

    default SecretReferenceUsageType webhookSecretUsage() {
        return SecretReferenceUsageType.webhook_verification;
    }

    default String defaultManifestPath() {
        return "";
    }

    String resolveRuntimeSecret(String reference);
}
