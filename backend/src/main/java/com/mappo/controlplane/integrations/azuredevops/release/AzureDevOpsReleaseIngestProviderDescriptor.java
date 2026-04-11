package com.mappo.controlplane.integrations.azuredevops.release;

import com.mappo.controlplane.application.releaseingest.ReleaseIngestDefaultSecretReferences;
import com.mappo.controlplane.application.releaseingest.ReleaseIngestProviderDescriptor;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import org.springframework.stereotype.Component;

@Component
class AzureDevOpsReleaseIngestProviderDescriptor implements ReleaseIngestProviderDescriptor {

    private final MappoProperties properties;

    AzureDevOpsReleaseIngestProviderDescriptor(MappoProperties properties) {
        this.properties = properties;
    }

    @Override
    public ReleaseIngestProviderType provider() {
        return ReleaseIngestProviderType.azure_devops;
    }

    @Override
    public String defaultSecretReference() {
        return ReleaseIngestDefaultSecretReferences.AZURE_DEVOPS_WEBHOOK_SECRET_REF;
    }

    @Override
    public SecretReferenceProviderType secretReferenceProvider() {
        return SecretReferenceProviderType.azure_devops;
    }

    @Override
    public String resolveRuntimeSecret(String reference) {
        if (defaultSecretReference().equals(normalize(reference))) {
            return normalize(properties.getAzureDevOps().getWebhookSecret());
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
