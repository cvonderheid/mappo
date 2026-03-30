package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProviderConnectionCreateRequest;
import com.mappo.controlplane.api.request.ProviderConnectionPatchRequest;
import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProviderConnectionMutationService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,126}[a-z0-9])?$");

    public ProviderConnectionMutationRecord fromCreate(ProviderConnectionCreateRequest request) {
        String id = requiredId(request.id());
        String name = requiredName(request.name());
        ProviderConnectionProviderType provider = requiredProvider(request.provider());
        boolean enabled = request.enabled() == null || Boolean.TRUE.equals(request.enabled());
        String organizationFilter = normalizeOrganizationFilter(request.organizationFilter());
        String personalAccessTokenRef = normalizePersonalAccessTokenRef(request.personalAccessTokenRef(), provider);
        return new ProviderConnectionMutationRecord(
            id,
            name,
            provider,
            enabled,
            organizationFilter,
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "provider connection name must not be blank");
        }
        ProviderConnectionProviderType provider = patch.provider() == null ? current.provider() : patch.provider();
        boolean enabled = patch.enabled() == null ? current.enabled() : patch.enabled();
        String organizationFilter = patch.organizationFilter() == null
            ? normalizeOrganizationFilter(current.organizationFilter())
            : normalizeOrganizationFilter(patch.organizationFilter());
        String personalAccessTokenRef = patch.personalAccessTokenRef() == null
            ? normalizePersonalAccessTokenRef(current.personalAccessTokenRef(), provider)
            : normalizePersonalAccessTokenRef(patch.personalAccessTokenRef(), provider);
        return new ProviderConnectionMutationRecord(
            id,
            requiredName(name),
            requiredProvider(provider),
            enabled,
            organizationFilter,
            personalAccessTokenRef
        );
    }

    private ProviderConnectionMutationRecord toMutation(ProviderConnectionRecord connection) {
        return new ProviderConnectionMutationRecord(
            requiredId(connection.id()),
            requiredName(connection.name()),
            requiredProvider(connection.provider()),
            connection.enabled(),
            normalizeOrganizationFilter(connection.organizationFilter()),
            normalizePersonalAccessTokenRef(connection.personalAccessTokenRef(), connection.provider())
        );
    }

    private String requiredId(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "provider connection id must not be blank");
        }
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "provider connection id must match " + ID_PATTERN.pattern());
        }
        return normalized;
    }

    private String requiredName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "provider connection name must not be blank");
        }
        return normalized;
    }

    private ProviderConnectionProviderType requiredProvider(ProviderConnectionProviderType value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "provider connection provider is required");
        }
        return value;
    }

    private String normalizeOrganizationFilter(String value) {
        return normalize(value);
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
                "literal PAT values are not supported; use a secret reference key or env:VAR_NAME."
            );
        }
        if (ProviderConnectionSecretResolver.AZURE_DEVOPS_PAT_SECRET_REF.equals(normalized)) {
            return normalized;
        }
        if (normalized.startsWith("env:") && normalize(normalized.substring("env:".length())).length() > 0) {
            return normalized;
        }
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "personalAccessTokenRef must be "
                + ProviderConnectionSecretResolver.AZURE_DEVOPS_PAT_SECRET_REF
                + " or env:VAR_NAME for Azure DevOps provider connections."
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
