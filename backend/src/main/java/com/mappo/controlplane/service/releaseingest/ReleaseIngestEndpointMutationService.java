package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseIngestEndpointCreateRequest;
import com.mappo.controlplane.api.request.ReleaseIngestEndpointPatchRequest;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import java.util.LinkedHashMap;
import java.util.Map;
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
        Map<String, Object> sourceConfig = normalizedConfig(request.sourceConfig());
        String secretRef = normalize(request.secretRef());
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
            manifestPath,
            sourceConfig
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "release ingest endpoint name must not be blank");
        }
        ReleaseIngestProviderType provider = patch.provider() == null ? current.provider() : patch.provider();
        boolean enabled = patch.enabled() == null ? current.enabled() : patch.enabled();
        String secretRef = patch.secretRef() == null ? normalize(current.secretRef()) : normalize(patch.secretRef());
        String repoFilter = patch.repoFilter() == null ? normalize(current.repoFilter()) : normalize(patch.repoFilter());
        String branchFilter = patch.branchFilter() == null ? normalize(current.branchFilter()) : normalize(patch.branchFilter());
        String pipelineIdFilter = patch.pipelineIdFilter() == null
            ? normalize(current.pipelineIdFilter())
            : normalize(patch.pipelineIdFilter());
        String manifestPath = patch.manifestPath() == null
            ? normalizeManifestPath(current.manifestPath(), provider)
            : normalizeManifestPath(patch.manifestPath(), provider);

        Map<String, Object> sourceConfig = patch.sourceConfig() == null
            ? normalizedConfig(current.sourceConfig())
            : normalizedConfig(patch.sourceConfig());
        return new ReleaseIngestEndpointMutationRecord(
            id,
            requiredName(name),
            requiredProvider(provider),
            enabled,
            secretRef,
            repoFilter,
            branchFilter,
            pipelineIdFilter,
            manifestPath,
            sourceConfig
        );
    }

    private ReleaseIngestEndpointMutationRecord toMutation(ReleaseIngestEndpointRecord endpoint) {
        return new ReleaseIngestEndpointMutationRecord(
            requiredId(endpoint.id()),
            requiredName(endpoint.name()),
            requiredProvider(endpoint.provider()),
            endpoint.enabled(),
            normalize(endpoint.secretRef()),
            normalize(endpoint.repoFilter()),
            normalize(endpoint.branchFilter()),
            normalize(endpoint.pipelineIdFilter()),
            normalizeManifestPath(endpoint.manifestPath(), endpoint.provider()),
            normalizedConfig(endpoint.sourceConfig())
        );
    }

    private String requiredId(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release ingest endpoint id must not be blank");
        }
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release ingest endpoint id must match " + ID_PATTERN.pattern());
        }
        return normalized;
    }

    private String requiredName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release ingest endpoint name must not be blank");
        }
        return normalized;
    }

    private ReleaseIngestProviderType requiredProvider(ReleaseIngestProviderType value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release ingest endpoint provider is required");
        }
        return value;
    }

    private Map<String, Object> normalizedConfig(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        value.forEach((key, itemValue) -> {
            String normalizedKey = normalize(key);
            if (!normalizedKey.isBlank() && itemValue != null) {
                normalized.put(normalizedKey, itemValue);
            }
        });
        return normalized;
    }

    private String normalizeManifestPath(String value, ReleaseIngestProviderType provider) {
        String normalized = normalize(value).replaceFirst("^/+", "");
        if (normalized.isBlank() && provider == ReleaseIngestProviderType.github) {
            return "releases/releases.manifest.json";
        }
        return normalized;
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
