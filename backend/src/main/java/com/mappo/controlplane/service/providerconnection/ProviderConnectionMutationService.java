package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProviderConnectionCreateRequest;
import com.mappo.controlplane.api.request.ProviderConnectionPatchRequest;
import com.mappo.controlplane.api.request.ProviderConnectionVerifyRequest;
import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;
import com.mappo.controlplane.infrastructure.azure.auth.AzureKeyVaultSecretResolver;
import com.mappo.controlplane.integrations.azuredevops.common.AzureDevOpsUrlNormalizer;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.model.SecretReferenceRecord;
import com.mappo.controlplane.service.secretreference.SecretReferenceCatalogService;
import com.mappo.controlplane.service.secretreference.SecretReferenceResolver;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderConnectionMutationService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,126}[a-z0-9])?$");
    private final SecretReferenceCatalogService secretReferenceCatalogService;

    public ProviderConnectionMutationRecord fromCreate(ProviderConnectionCreateRequest request) {
        String id = requiredId(request.id());
        String name = requiredName(request.name());
        ProviderConnectionProviderType provider = requiredProvider(request.provider());
        boolean enabled = request.enabled() == null || Boolean.TRUE.equals(request.enabled());
        String organizationUrl = normalizeOrganizationUrl(request.organizationUrl(), provider);
        String personalAccessTokenRef = normalizePersonalAccessTokenRef(request.personalAccessTokenRef(), provider);
        return new ProviderConnectionMutationRecord(
            id,
            name,
            provider,
            enabled,
            organizationUrl,
            personalAccessTokenRef
        );
    }

    public ProviderConnectionMutationRecord fromPatch(
        ProviderConnectionRecord current,
        ProviderConnectionPatchRequest patch
    ) {
        if (patch == null) {
            return toMutation(current);
        }
        String id = requiredId(current.id());
        String name = firstNonBlank(patch.name(), current.name());
        if (name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "deployment connection name must not be blank");
        }
        ProviderConnectionProviderType provider = patch.provider() == null ? current.provider() : patch.provider();
        boolean enabled = patch.enabled() == null ? current.enabled() : patch.enabled();
        String organizationUrl = patch.organizationUrl() == null
            ? normalizeOrganizationUrl(current.organizationUrl(), provider)
            : normalizeOrganizationUrl(patch.organizationUrl(), provider);
        String personalAccessTokenRef = patch.personalAccessTokenRef() == null
            ? normalizePersonalAccessTokenRef(current.personalAccessTokenRef(), provider)
            : normalizePersonalAccessTokenRef(patch.personalAccessTokenRef(), provider);
        return new ProviderConnectionMutationRecord(
            id,
            requiredName(name),
            requiredProvider(provider),
            enabled,
            organizationUrl,
            personalAccessTokenRef
        );
    }

    public ProviderConnectionMutationRecord fromVerification(ProviderConnectionVerifyRequest request) {
        ProviderConnectionProviderType provider = request == null || request.provider() == null
            ? ProviderConnectionProviderType.azure_devops
            : request.provider();
        return new ProviderConnectionMutationRecord(
            normalize(request == null ? "" : request.id()).isBlank() ? "__preview__" : normalize(request.id()),
            "Preview",
            requiredProvider(provider),
            true,
            normalizeOrganizationUrl(request == null ? "" : request.organizationUrl(), provider),
            normalizePersonalAccessTokenRef(request == null ? "" : request.personalAccessTokenRef(), provider)
        );
    }

    private ProviderConnectionMutationRecord toMutation(ProviderConnectionRecord connection) {
        return new ProviderConnectionMutationRecord(
            requiredId(connection.id()),
            requiredName(connection.name()),
            requiredProvider(connection.provider()),
            connection.enabled(),
            normalizeOrganizationUrl(connection.organizationUrl(), connection.provider()),
            normalizePersonalAccessTokenRef(connection.personalAccessTokenRef(), connection.provider())
        );
    }

    private String requiredId(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "deployment connection id must not be blank");
        }
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "deployment connection id must match " + ID_PATTERN.pattern());
        }
        return normalized;
    }

    private String requiredName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "deployment connection name must not be blank");
        }
        return normalized;
    }

    private ProviderConnectionProviderType requiredProvider(ProviderConnectionProviderType value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "deployment connection provider is required");
        }
        return value;
    }

    private String normalizeOrganizationUrl(String value, ProviderConnectionProviderType provider) {
        String normalized = normalize(value);
        if (provider != ProviderConnectionProviderType.azure_devops) {
            return "";
        }
        if (normalized.isBlank()) {
            return "";
        }
        return AzureDevOpsUrlNormalizer.normalizeOrganizationUrl(normalized, "https://dev.azure.com");
    }

    private String normalizePersonalAccessTokenRef(String value, ProviderConnectionProviderType provider) {
        String normalized = normalize(value);
        if (provider != ProviderConnectionProviderType.azure_devops) {
            return "";
        }
        if (normalized.isBlank()) {
            return ProviderConnectionSecretResolver.AZURE_DEVOPS_PAT_SECRET_REF;
        }
        if (normalized.startsWith("literal:")) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "literal PAT values are not supported; use the MAPPO backend secret, env:VAR_NAME, or kv:secret-name."
            );
        }
        if (ProviderConnectionSecretResolver.AZURE_DEVOPS_PAT_SECRET_REF.equals(normalized)) {
            return normalized;
        }
        if (normalized.startsWith(SecretReferenceResolver.SECRET_REFERENCE_PREFIX)) {
            String secretReferenceId = normalize(normalized.substring(SecretReferenceResolver.SECRET_REFERENCE_PREFIX.length()));
            SecretReferenceRecord secretReference = secretReferenceCatalogService.getRequired(secretReferenceId);
            if (
                secretReference.provider() != SecretReferenceProviderType.azure_devops
                || secretReference.usage() != SecretReferenceUsageType.deployment_api_credential
            ) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "secret reference " + secretReferenceId + " is not an Azure DevOps deployment API credential."
                );
            }
            return SecretReferenceResolver.SECRET_REFERENCE_PREFIX + secretReferenceId;
        }
        if (normalized.startsWith("env:") && normalize(normalized.substring("env:".length())).length() > 0) {
            return normalized;
        }
        if (
            normalized.startsWith(AzureKeyVaultSecretResolver.KEY_VAULT_PREFIX)
            && normalize(normalized.substring(AzureKeyVaultSecretResolver.KEY_VAULT_PREFIX.length())).length() > 0
        ) {
            return normalized;
        }
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "personalAccessTokenRef must be "
                + ProviderConnectionSecretResolver.AZURE_DEVOPS_PAT_SECRET_REF
                + ", secret:reference-id, env:VAR_NAME, or kv:secret-name for Azure DevOps deployment connections."
        );
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
