package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import com.mappo.controlplane.api.request.ReleaseExecutionSettingsRequest;
import com.mappo.controlplane.api.request.ReleaseManifestIngestRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.service.ReleaseService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReleaseManifestIngestService {

    private final ReleaseService releaseService;
    private final ReleaseManifestSourceClient sourceClient;
    private final MappoProperties properties;
    private final ObjectMapper objectMapper;

    public ReleaseManifestIngestResultRecord ingestGithubManifest(ReleaseManifestIngestRequest request) {
        String repo = normalize(firstNonBlank(request == null ? null : request.repo(), properties.getManagedAppReleaseRepo()));
        String path = normalize(firstNonBlank(request == null ? null : request.path(), properties.getManagedAppReleasePath()));
        String ref = normalize(firstNonBlank(request == null ? null : request.ref(), properties.getManagedAppReleaseRef()));
        boolean allowDuplicates = request != null && Boolean.TRUE.equals(request.allowDuplicates());

        if (repo.isBlank() || path.isBlank() || ref.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "repo, path, and ref are required for release manifest ingest");
        }

        String manifest = sourceClient.fetchGithubManifest(repo, path, ref);
        List<ReleaseCreateRequest> requests = parseManifest(manifest);

        Set<String> existingKeys = new LinkedHashSet<>();
        if (!allowDuplicates) {
            for (ReleaseRecord row : releaseService.listReleases()) {
                existingKeys.add(releaseKey(row.sourceRef(), row.sourceVersion()));
            }
        }

        int created = 0;
        int skipped = 0;
        List<String> createdReleaseIds = new ArrayList<>();
        for (ReleaseCreateRequest candidate : requests) {
            String key = releaseKey(candidate.sourceRef(), candidate.sourceVersion());
            if (!allowDuplicates && existingKeys.contains(key)) {
                skipped += 1;
                continue;
            }
            ReleaseRecord createdRelease = releaseService.createRelease(candidate);
            created += 1;
            createdReleaseIds.add(createdRelease.id());
            existingKeys.add(key);
        }

        return new ReleaseManifestIngestResultRecord(
            repo,
            path,
            ref,
            requests.size(),
            created,
            skipped,
            List.copyOf(createdReleaseIds)
        );
    }

    private List<ReleaseCreateRequest> parseManifest(String rawPayload) {
        Object parsed;
        try {
            parsed = objectMapper.readValue(rawPayload, Object.class);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest is not valid JSON: " + exception.getMessage());
        }

        Object releasesPayload;
        if (parsed instanceof List<?> list) {
            releasesPayload = list;
        } else if (parsed instanceof Map<?, ?> map) {
            releasesPayload = map.get("releases");
            if (releasesPayload == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest object must include a 'releases' array");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest must be an array or an object with a 'releases' array");
        }

        if (!(releasesPayload instanceof List<?> releases)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest 'releases' must be an array");
        }

        List<ReleaseCreateRequest> normalized = new ArrayList<>();
        for (int index = 0; index < releases.size(); index++) {
            Object item = releases.get(index);
            if (!(item instanceof Map<?, ?> row)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest row #%d is not an object".formatted(index + 1));
            }

            String sourceRef = firstNonBlank(
                stringValue(row.get("source_ref")),
                stringValue(row.get("sourceRef")),
                stringValue(row.get("template_spec_id"))
            );
            String sourceVersion = firstNonBlank(
                stringValue(row.get("source_version")),
                stringValue(row.get("sourceVersion")),
                stringValue(row.get("template_spec_version"))
            );
            if (sourceRef.isBlank() || sourceVersion.isBlank()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "release manifest row #%d missing required fields: source_ref and source_version".formatted(index + 1)
                );
            }

            Map<?, ?> executionSettingsRow = asMap(
                firstNonNull(row.get("execution_settings"), row.get("executionSettings"), row.get("deployment_mode_settings"))
            );
            Map<String, String> parameterDefaults = sanitizeStringMap(asMap(firstNonNull(row.get("parameter_defaults"), row.get("parameterDefaults"))));
            List<String> verificationHints = sanitizeStringList(asList(firstNonNull(row.get("verification_hints"), row.get("verificationHints"))));

            ReleaseExecutionSettingsRequest executionSettings = new ReleaseExecutionSettingsRequest(
                enumValue(
                    firstNonNull(executionSettingsRow.get("arm_mode"), executionSettingsRow.get("armMode")),
                    MappoArmDeploymentMode.class,
                    MappoArmDeploymentMode.incremental,
                    index,
                    "execution_settings.arm_mode"
                ),
                booleanValue(
                    firstNonNull(executionSettingsRow.get("what_if_on_canary"), executionSettingsRow.get("whatIfOnCanary")),
                    false
                ),
                booleanValue(
                    firstNonNull(executionSettingsRow.get("verify_after_deploy"), executionSettingsRow.get("verifyAfterDeploy")),
                    true
                )
            );

            normalized.add(new ReleaseCreateRequest(
                sourceRef,
                sourceVersion,
                enumValue(firstNonNull(row.get("source_type"), row.get("sourceType")), MappoReleaseSourceType.class, MappoReleaseSourceType.template_spec, index, "source_type"),
                nullable(firstNonBlank(stringValue(row.get("source_version_ref")), stringValue(row.get("sourceVersionRef")))),
                enumValue(firstNonNull(row.get("deployment_scope"), row.get("deploymentScope")), MappoDeploymentScope.class, MappoDeploymentScope.resource_group, index, "deployment_scope"),
                executionSettings,
                parameterDefaults,
                normalize(firstNonBlank(stringValue(row.get("release_notes")), stringValue(row.get("releaseNotes")))),
                verificationHints
            ));
        }
        return normalized;
    }

    private Map<String, String> sanitizeStringMap(Map<?, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            if (!key.isBlank()) {
                out.put(key, normalize(entry.getValue()));
            }
        }
        return out;
    }

    private List<String> sanitizeStringList(List<?> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object value : source) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest contains a non-object settings/defaults block");
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<?> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest verification_hints must be an array");
        }
        return list;
    }

    private Boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = normalize(value);
        if (text.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(text);
    }

    private <E extends Enum<E>> E enumValue(Object value, Class<E> type, E fallback, int index, String fieldName) {
        if (value == null) {
            return fallback;
        }
        String text = normalize(value);
        if (text.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "release manifest row #%d has invalid %s: %s".formatted(index + 1, fieldName, text)
            );
        }
    }

    private String releaseKey(String sourceRef, String sourceVersion) {
        return normalize(sourceRef) + "::" + normalize(sourceVersion);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
