package com.mappo.controlplane.service.secretreference;

import com.mappo.controlplane.model.SecretReferenceRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretReferenceResolver {

    public static final String SECRET_REFERENCE_PREFIX = "secret:";

    private final SecretReferenceCatalogService secretReferenceCatalogService;

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

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
