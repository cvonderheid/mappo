package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.application.providerconnection.ProviderConnectionDefaultSecretReferences;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.service.secretreference.SecretReferenceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderConnectionSecretResolver {

    public static final String AZURE_DEVOPS_PAT_SECRET_REF =
        ProviderConnectionDefaultSecretReferences.AZURE_DEVOPS_PAT_SECRET_REF;

    private final MappoProperties properties;
    private final SecretReferenceResolver secretReferenceResolver;

    public String defaultPersonalAccessTokenRef(ProviderConnectionProviderType provider) {
        if (provider == ProviderConnectionProviderType.azure_devops) {
            return AZURE_DEVOPS_PAT_SECRET_REF;
        }
        return "";
    }

    public String resolvePersonalAccessToken(ProviderConnectionRecord connection) {
        if (connection == null || connection.provider() == null) {
            return "";
        }
        return resolvePersonalAccessToken(connection.provider(), connection.personalAccessTokenRef());
    }

    public String resolvePersonalAccessToken(
        ProviderConnectionProviderType provider,
        String personalAccessTokenRef
    ) {
        ProviderConnectionProviderType normalizedProvider =
            provider == null ? ProviderConnectionProviderType.azure_devops : provider;
        if (normalizedProvider != ProviderConnectionProviderType.azure_devops) {
            return "";
        }
        String reference = normalize(personalAccessTokenRef);
        if (reference.isBlank()) {
            reference = AZURE_DEVOPS_PAT_SECRET_REF;
        }
        reference = secretReferenceResolver.resolveBackendReference(reference);
        if (AZURE_DEVOPS_PAT_SECRET_REF.equals(reference)) {
            return normalize(properties.getAzureDevOps().getPersonalAccessToken());
        }
        return secretReferenceResolver.resolveDynamicSecretValue(reference);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
