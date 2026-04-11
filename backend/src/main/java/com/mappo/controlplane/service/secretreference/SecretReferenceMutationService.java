package com.mappo.controlplane.service.secretreference;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.SecretReferenceCreateRequest;
import com.mappo.controlplane.api.request.SecretReferencePatchRequest;
import com.mappo.controlplane.application.releaseingest.ReleaseIngestDefaultSecretReferences;
import com.mappo.controlplane.domain.secretreference.SecretReferenceModeType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;
import com.mappo.controlplane.model.SecretReferenceRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionSecretResolver;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SecretReferenceMutationService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,126}[a-z0-9])?$");

    public SecretReferenceMutationRecord fromCreate(SecretReferenceCreateRequest request) {
        return new SecretReferenceMutationRecord(
            requiredId(request.id()),
            requiredName(request.name()),
            requiredProvider(request.provider()),
            requiredUsage(request.usage()),
            requiredMode(request.mode()),
            normalizeBackendRef(request.backendRef(), request.provider(), request.usage(), request.mode())
        );
    }

    public SecretReferenceMutationRecord fromPatch(SecretReferenceRecord current, SecretReferencePatchRequest patch) {
        if (patch == null) {
            return toMutation(current);
        }
        SecretReferenceProviderType provider = patch.provider() == null ? current.provider() : patch.provider();
        SecretReferenceUsageType usage = patch.usage() == null ? current.usage() : patch.usage();
        SecretReferenceModeType mode = patch.mode() == null ? current.mode() : patch.mode();
        String backendRef = patch.backendRef() == null
            ? normalizeBackendRef(current.backendRef(), provider, usage, mode)
            : normalizeBackendRef(patch.backendRef(), provider, usage, mode);
        return new SecretReferenceMutationRecord(
            requiredId(current.id()),
            requiredName(firstNonBlank(patch.name(), current.name())),
            requiredProvider(provider),
            requiredUsage(usage),
            requiredMode(mode),
            backendRef
        );
    }

    private SecretReferenceMutationRecord toMutation(SecretReferenceRecord current) {
        return new SecretReferenceMutationRecord(
            requiredId(current.id()),
            requiredName(current.name()),
            requiredProvider(current.provider()),
            requiredUsage(current.usage()),
            requiredMode(current.mode()),
            normalizeBackendRef(current.backendRef(), current.provider(), current.usage(), current.mode())
        );
    }

    private String normalizeBackendRef(
        String value,
        SecretReferenceProviderType provider,
        SecretReferenceUsageType usage,
        SecretReferenceModeType mode
    ) {
        String normalized = normalize(value);
        return switch (requiredMode(mode)) {
            case mappo_default -> defaultReference(requiredProvider(provider), requiredUsage(usage));
            case environment_variable -> {
                if (normalized.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "environment variable name is required");
                }
                String envName = normalized.startsWith("env:") ? normalize(normalized.substring("env:".length())) : normalized;
                if (envName.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "environment variable name is required");
                }
                yield "env:" + envName;
            }
            case key_vault_secret -> {
                if (normalized.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Azure Key Vault secret name is required");
                }
                String secretName = normalized.startsWith(SecretReferenceResolver.KEY_VAULT_REFERENCE_PREFIX)
                    ? normalize(normalized.substring(SecretReferenceResolver.KEY_VAULT_REFERENCE_PREFIX.length()))
                    : normalized;
                if (secretName.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Azure Key Vault secret name is required");
                }
                yield SecretReferenceResolver.KEY_VAULT_REFERENCE_PREFIX + secretName;
            }
        };
    }

    private String defaultReference(SecretReferenceProviderType provider, SecretReferenceUsageType usage) {
        if (usage == SecretReferenceUsageType.deployment_api_credential && provider == SecretReferenceProviderType.azure_devops) {
            return ProviderConnectionSecretResolver.AZURE_DEVOPS_PAT_SECRET_REF;
        }
        if (usage == SecretReferenceUsageType.webhook_verification && provider == SecretReferenceProviderType.azure_devops) {
            return ReleaseIngestDefaultSecretReferences.AZURE_DEVOPS_WEBHOOK_SECRET_REF;
        }
        if (usage == SecretReferenceUsageType.webhook_verification && provider == SecretReferenceProviderType.github) {
            return ReleaseIngestDefaultSecretReferences.GITHUB_WEBHOOK_SECRET_REF;
        }
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "MAPPO does not have a built-in runtime secret for " + provider.name() + " " + usage.name() + "."
        );
    }

    private String requiredId(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference id must not be blank");
        }
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference id must match " + ID_PATTERN.pattern());
        }
        return normalized;
    }

    private String requiredName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference name must not be blank");
        }
        return normalized;
    }

    private SecretReferenceProviderType requiredProvider(SecretReferenceProviderType value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference provider is required");
        }
        return value;
    }

    private SecretReferenceUsageType requiredUsage(SecretReferenceUsageType value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference usage is required");
        }
        return value;
    }

    private SecretReferenceModeType requiredMode(SecretReferenceModeType value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference mode is required");
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
