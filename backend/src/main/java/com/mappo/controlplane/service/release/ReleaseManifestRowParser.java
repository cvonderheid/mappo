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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ReleaseManifestRowParser {

    public ReleaseCreateRequest parse(Map<?, ?> row, int index) {
        String sourceRef = firstNonBlank(
            stringValue(row.get("source_ref")),
            stringValue(row.get("sourceRef"))
        );
        String sourceVersion = firstNonBlank(
            stringValue(row.get("source_version")),
            stringValue(row.get("sourceVersion"))
        );
        String sourceTypeValue = firstNonBlank(
            stringValue(row.get("source_type")),
            stringValue(row.get("sourceType"))
        );
        String sourceVersionRef = nullable(
            firstNonBlank(
                stringValue(row.get("source_version_ref")),
                stringValue(row.get("sourceVersionRef"))
            )
        );
        if (sourceRef.isBlank() || sourceVersion.isBlank() || sourceTypeValue.isBlank() || sourceVersionRef == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "release manifest row #%d missing required fields: source_ref, source_version, source_type, and source_version_ref".formatted(
                    index + 1
                )
            );
        }

        MappoReleaseSourceType sourceType = enumValue(
            sourceTypeValue,
            MappoReleaseSourceType.class,
            null,
            index,
            "source_type"
        );

        Map<?, ?> executionSettingsRow = asMap(
            firstNonNull(row.get("execution_settings"), row.get("executionSettings"), row.get("deployment_mode_settings"))
        );
        Map<String, String> parameterDefaults = sanitizeStringMap(asMap(firstNonNull(row.get("parameter_defaults"), row.get("parameterDefaults"))));
        Map<String, String> externalInputs = sanitizeStringMap(
            asMap(
                firstNonNull(
                    row.get("external_inputs"),
                    row.get("externalInputs"),
                    row.get("deployment_inputs"),
                    row.get("deploymentInputs"),
                    row.get("pipeline_inputs"),
                    row.get("pipelineInputs")
                )
            )
        );
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

        return new ReleaseCreateRequest(
            "",
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
            externalInputs,
            normalize(firstNonBlank(stringValue(row.get("release_notes")), stringValue(row.get("releaseNotes")))),
            verificationHints
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

    private Map<?, ?> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest contains a non-object settings/defaults block");
        }
        return map;
    }

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
