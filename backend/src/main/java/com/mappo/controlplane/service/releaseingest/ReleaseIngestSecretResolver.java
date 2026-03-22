package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseIngestSecretResolver {

    public static final String GITHUB_SECRET_REF = "mappo.managed-app-release.webhook-secret";
    public static final String AZURE_DEVOPS_SECRET_REF = "mappo.azure-devops.webhook-secret";

    private final MappoProperties properties;

    public String resolveConfiguredSecret(ReleaseIngestEndpointRecord endpoint) {
        if (endpoint == null || endpoint.provider() == null) {
            return "";
        }
        return resolveConfiguredSecret(endpoint.provider(), endpoint.secretRef());
    }

    public String resolveConfiguredSecret(ReleaseIngestProviderType provider, String secretRef) {
        ReleaseIngestProviderType normalizedProvider = provider == null ? ReleaseIngestProviderType.github : provider;
        String reference = normalize(secretRef);
        if (reference.isBlank()) {
            reference = defaultSecretReference(normalizedProvider);
        }
        if (GITHUB_SECRET_REF.equals(reference)) {
            return normalize(properties.getManagedAppRelease().getWebhookSecret());
        }
        if (AZURE_DEVOPS_SECRET_REF.equals(reference)) {
            return normalize(properties.getAzureDevOps().getWebhookSecret());
        }
        if (reference.startsWith("literal:")) {
            return normalize(reference.substring("literal:".length()));
        }
        return "";
    }

    public String defaultSecretReference(ReleaseIngestProviderType provider) {
        if (provider == ReleaseIngestProviderType.azure_devops) {
            return AZURE_DEVOPS_SECRET_REF;
        }
        return GITHUB_SECRET_REF;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
