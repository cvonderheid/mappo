package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import com.mappo.controlplane.api.request.ReleaseExecutionSettingsRequest;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ReleaseManifestParser {

    private final ObjectMapper objectMapper;

    public ParsedReleaseManifest parse(String rawPayload) {
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
        int ignoredCount = 0;
        for (int index = 0; index < releases.size(); index += 1) {
            Object item = releases.get(index);
            if (!(item instanceof Map<?, ?> row)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest row #%d is not an object".formatted(index + 1));
            }
            if (!shouldIngestRow(row, index)) {
                ignoredCount += 1;
                continue;
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

            MappoReleaseSourceType sourceType = enumValue(
                firstNonNull(row.get("source_type"), row.get("sourceType")),
                MappoReleaseSourceType.class,
                MappoReleaseSourceType.template_spec,
                index,
                "source_type"
            );
            String sourceVersionRef = nullable(
                firstNonBlank(
                    stringValue(row.get("source_version_ref")),
                    stringValue(row.get("sourceVersionRef")),
                    stringValue(row.get("template_spec_version_id"))
                )
            );
            if (sourceType != MappoReleaseSourceType.template_spec && sourceVersionRef == null) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "release manifest row #%d missing required field: source_version_ref".formatted(index + 1)
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
                sourceType,
                sourceVersionRef,
                enumValue(
                    firstNonNull(row.get("deployment_scope"), row.get("deploymentScope")),
                    MappoDeploymentScope.class,
                    MappoDeploymentScope.resource_group,
                    index,
                    "deployment_scope"
                ),
                executionSettings,
                parameterDefaults,
                normalize(firstNonBlank(stringValue(row.get("release_notes")), stringValue(row.get("releaseNotes")))),
                verificationHints
            ));
        }

        return new ParsedReleaseManifest(releases.size(), ignoredCount, List.copyOf(normalized));
    }

    @SuppressWarnings("unchecked")
    Map<?, ?> readJsonObject(String rawPayload, String errorPrefix) {
        Object parsed;
        try {
            parsed = objectMapper.readValue(rawPayload, Object.class);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorPrefix + ": " + exception.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorPrefix + ": expected a JSON object");
        }
        return map;
    }

    private boolean shouldIngestRow(Map<?, ?> row, int index) {
        String publicationStatus = normalize(firstNonNull(row.get("publication_status"), row.get("publicationStatus")));
        if (publicationStatus.isBlank() || "published".equals(publicationStatus)) {
            return true;
        }
        if ("draft".equals(publicationStatus)) {
            return false;
        }
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "release manifest row #%d has invalid publication_status: %s".formatted(index + 1, publicationStatus)
        );
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
