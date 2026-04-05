package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseIngestEndpointCreateRequest;
import com.mappo.controlplane.api.request.ReleaseIngestEndpointPatchRequest;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.infrastructure.azure.auth.AzureKeyVaultSecretResolver;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReleaseIngestEndpointMutationService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,126}[a-z0-9])?$");

    public ReleaseIngestEndpointMutationRecord fromCreate(ReleaseIngestEndpointCreateRequest request) {
        String id = requiredId(request.id());
        String name = requiredName(request.name());
        ReleaseIngestProviderType provider = requiredProvider(request.provider());
        boolean enabled = request.enabled() == null || Boolean.TRUE.equals(request.enabled());
        String secretRef = normalizeSecretRef(request.secretRef(), provider);
        String repoFilter = normalize(request.repoFilter());
        String branchFilter = normalize(request.branchFilter());
        String pipelineIdFilter = normalize(request.pipelineIdFilter());
        String manifestPath = normalizeManifestPath(request.manifestPath(), provider);
        return new ReleaseIngestEndpointMutationRecord(
            id,
            name,
            provider,
            enabled,
            secretRef,
            repoFilter,
            branchFilter,
            pipelineIdFilter,
            manifestPath
        );
    }

    public ReleaseIngestEndpointMutationRecord fromPatch(
        ReleaseIngestEndpointRecord current,
        ReleaseIngestEndpointPatchRequest patch
    ) {
        if (patch == null) {
            return toMutation(current);
        }
        String id = requiredId(current.id());
        String name = firstNonBlank(patch.name(), current.name());
        if (name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source name must not be blank");
        }
        if (patch.provider() != null && patch.provider() != current.provider()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "release source provider cannot change after creation; create a new release source instead."
            );
        }
        ReleaseIngestProviderType provider = current.provider();
        boolean enabled = patch.enabled() == null ? current.enabled() : patch.enabled();
        String secretRef = patch.secretRef() == null
            ? normalizeSecretRef(current.secretRef(), provider)
            : normalizeSecretRef(patch.secretRef(), provider);
        String repoFilter = patch.repoFilter() == null ? normalize(current.repoFilter()) : normalize(patch.repoFilter());
        String branchFilter = patch.branchFilter() == null ? normalize(current.branchFilter()) : normalize(patch.branchFilter());
        String pipelineIdFilter = patch.pipelineIdFilter() == null
            ? normalize(current.pipelineIdFilter())
            : normalize(patch.pipelineIdFilter());
        String manifestPath = patch.manifestPath() == null
            ? normalizeManifestPath(current.manifestPath(), provider)
            : normalizeManifestPath(patch.manifestPath(), provider);
        return new ReleaseIngestEndpointMutationRecord(
            id,
            requiredName(name),
            requiredProvider(provider),
            enabled,
            secretRef,
            repoFilter,
            branchFilter,
            pipelineIdFilter,
            manifestPath
        );
    }

    private ReleaseIngestEndpointMutationRecord toMutation(ReleaseIngestEndpointRecord endpoint) {
        return new ReleaseIngestEndpointMutationRecord(
            requiredId(endpoint.id()),
            requiredName(endpoint.name()),
            requiredProvider(endpoint.provider()),
            endpoint.enabled(),
            normalizeSecretRef(endpoint.secretRef(), endpoint.provider()),
            normalize(endpoint.repoFilter()),
            normalize(endpoint.branchFilter()),
            normalize(endpoint.pipelineIdFilter()),
            normalizeManifestPath(endpoint.manifestPath(), endpoint.provider())
        );
    }

    private String requiredId(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source id must not be blank");
        }
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source id must match " + ID_PATTERN.pattern());
        }
        return normalized;
    }

    private String requiredName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source name must not be blank");
        }
        return normalized;
    }

    private ReleaseIngestProviderType requiredProvider(ReleaseIngestProviderType value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source provider is required");
        }
        return value;
    }

    private String normalizeManifestPath(String value, ReleaseIngestProviderType provider) {
        String normalized = normalize(value).replaceFirst("^/+", "");
        if (normalized.isBlank() && provider == ReleaseIngestProviderType.github) {
            return "releases/releases.manifest.json";
        }
        return normalized;
    }

    private String normalizeSecretRef(String value, ReleaseIngestProviderType provider) {
        String normalized = normalize(value);
        String defaultReference = provider == ReleaseIngestProviderType.azure_devops
            ? ReleaseIngestSecretResolver.AZURE_DEVOPS_SECRET_REF
            : ReleaseIngestSecretResolver.GITHUB_SECRET_REF;
        if (normalized.isBlank()) {
            return defaultReference;
        }
        if (normalized.startsWith("literal:")) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "literal webhook secrets are not supported; use the provider default, env:VAR_NAME, or kv:secret-name."
            );
        }
        if (defaultReference.equals(normalized)) {
            return normalized;
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
            "secretRef must be " + defaultReference + ", env:VAR_NAME, or kv:secret-name."
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
