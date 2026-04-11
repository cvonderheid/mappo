package com.mappo.controlplane.integrations.github.release;

import com.mappo.controlplane.application.releaseingest.ReleaseIngestDefaultSecretReferences;
import com.mappo.controlplane.application.releaseingest.ReleaseIngestProviderDescriptor;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import org.springframework.stereotype.Component;

@Component
class GithubReleaseIngestProviderDescriptor implements ReleaseIngestProviderDescriptor {

    private final MappoProperties properties;

    GithubReleaseIngestProviderDescriptor(MappoProperties properties) {
        this.properties = properties;
    }

    @Override
    public ReleaseIngestProviderType provider() {
        return ReleaseIngestProviderType.github;
    }

    @Override
    public String defaultSecretReference() {
        return ReleaseIngestDefaultSecretReferences.GITHUB_WEBHOOK_SECRET_REF;
    }

    @Override
    public SecretReferenceProviderType secretReferenceProvider() {
        return SecretReferenceProviderType.github;
    }

    @Override
    public String defaultManifestPath() {
        return normalize(properties.getManagedAppRelease().getPath());
    }

    @Override
    public String resolveRuntimeSecret(String reference) {
        if (defaultSecretReference().equals(normalize(reference))) {
            return normalize(properties.getManagedAppRelease().getWebhookSecret());
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
