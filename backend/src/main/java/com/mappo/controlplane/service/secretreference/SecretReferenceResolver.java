package com.mappo.controlplane.service.secretreference;

import com.mappo.controlplane.application.secretreference.SecretBackendResolver;
import com.mappo.controlplane.application.secretreference.SecretReferencePrefixes;
import com.mappo.controlplane.model.SecretReferenceRecord;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretReferenceResolver {

    public static final String SECRET_REFERENCE_PREFIX = SecretReferencePrefixes.SECRET_REFERENCE_PREFIX;
    public static final String ENVIRONMENT_PREFIX = SecretReferencePrefixes.ENVIRONMENT_PREFIX;
    public static final String KEY_VAULT_REFERENCE_PREFIX = SecretReferencePrefixes.KEY_VAULT_REFERENCE_PREFIX;

    private final SecretReferenceCatalogService secretReferenceCatalogService;
    private final List<SecretBackendResolver> secretBackendResolvers;

    public String resolveBackendReference(String reference) {
        String normalized = normalize(reference);
        if (!normalized.startsWith(SECRET_REFERENCE_PREFIX)) {
            return normalized;
        }
        String secretReferenceId = normalize(normalized.substring(SECRET_REFERENCE_PREFIX.length()));
        if (secretReferenceId.isBlank()) {
            return "";
        }
        return secretReferenceCatalogService
            .getSecretReference(secretReferenceId)
            .map(SecretReferenceRecord::backendRef)
            .map(this::normalize)
            .orElse("");
    }

    public String toReferenceToken(String secretReferenceId) {
        String normalized = normalize(secretReferenceId);
        return normalized.isBlank() ? "" : SECRET_REFERENCE_PREFIX + normalized;
    }

    public String extractSecretReferenceId(String reference) {
        String normalized = normalize(reference);
        if (!normalized.startsWith(SECRET_REFERENCE_PREFIX)) {
            return "";
        }
        return normalize(normalized.substring(SECRET_REFERENCE_PREFIX.length()));
    }

    public String resolveDynamicSecretValue(String reference) {
        String resolvedReference = resolveBackendReference(reference);
        if (resolvedReference.startsWith(ENVIRONMENT_PREFIX)) {
            String envVarName = normalize(resolvedReference.substring(ENVIRONMENT_PREFIX.length()));
            return envVarName.isBlank() ? "" : normalize(System.getenv(envVarName));
        }
        return secretBackendResolvers.stream()
            .filter(resolver -> resolver.supports(resolvedReference))
            .findFirst()
            .map(resolver -> normalize(resolver.resolve(resolvedReference)))
            .orElse("");
    }

    public boolean isDynamicSecretReference(String reference) {
        String normalized = normalize(reference);
        if (
            normalized.startsWith(ENVIRONMENT_PREFIX)
            && !normalize(normalized.substring(ENVIRONMENT_PREFIX.length())).isBlank()
        ) {
            return true;
        }
        return secretBackendResolvers.stream().anyMatch(resolver -> resolver.supports(normalized));
    }

    public String keyVaultReference(String secretName) {
        String normalized = normalize(secretName);
        return normalized.isBlank() ? "" : KEY_VAULT_REFERENCE_PREFIX + normalized;
    }

    public String keyVaultSecretName(String reference) {
        String normalized = normalize(reference);
        if (!normalized.startsWith(KEY_VAULT_REFERENCE_PREFIX)) {
            return "";
        }
        return normalize(normalized.substring(KEY_VAULT_REFERENCE_PREFIX.length()));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
