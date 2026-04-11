package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.application.releaseingest.ReleaseIngestProviderDescriptor;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.service.secretreference.SecretReferenceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseIngestSecretResolver {

    private final ReleaseIngestProviderDescriptorRegistry releaseIngestProviderDescriptorRegistry;
    private final SecretReferenceResolver secretReferenceResolver;

    public String resolveConfiguredSecret(ReleaseIngestEndpointRecord endpoint) {
        if (endpoint == null || endpoint.provider() == null) {
            return "";
        }
        return resolveConfiguredSecret(endpoint.provider(), endpoint.secretRef());
    }

    public String resolveConfiguredSecret(ReleaseIngestProviderType provider, String secretRef) {
        ReleaseIngestProviderType normalizedProvider = provider == null ? ReleaseIngestProviderType.github : provider;
        ReleaseIngestProviderDescriptor descriptor = releaseIngestProviderDescriptorRegistry.getRequired(normalizedProvider);
        String reference = normalize(secretRef);
        if (reference.isBlank()) {
            reference = descriptor.defaultSecretReference();
        }
        reference = secretReferenceResolver.resolveBackendReference(reference);
        String runtimeSecret = descriptor.resolveRuntimeSecret(reference);
        if (!runtimeSecret.isBlank()) {
            return runtimeSecret;
        }
        return secretReferenceResolver.resolveDynamicSecretValue(reference);
    }

    public String defaultSecretReference(ReleaseIngestProviderType provider) {
        ReleaseIngestProviderType normalizedProvider = provider == null ? ReleaseIngestProviderType.github : provider;
        return releaseIngestProviderDescriptorRegistry.getRequired(normalizedProvider).defaultSecretReference();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
