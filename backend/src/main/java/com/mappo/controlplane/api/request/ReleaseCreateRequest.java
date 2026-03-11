package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.command.CreateReleaseCommand;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseCreateRequest(
    @NotBlank String projectId,
    @NotBlank String sourceRef,
    @NotBlank String sourceVersion,
    MappoReleaseSourceType sourceType,
    String sourceVersionRef,
    MappoDeploymentScope deploymentScope,
    ReleaseExecutionSettingsRequest executionSettings,
    Map<String, String> parameterDefaults,
    String releaseNotes,
    List<String> verificationHints
) {

    public CreateReleaseCommand toCommand() {
        return new CreateReleaseCommand(
            normalize(projectId),
            normalize(sourceRef),
            normalize(sourceVersion),
            sourceType == null ? MappoReleaseSourceType.template_spec : sourceType,
            nullable(sourceVersionRef),
            deploymentScope == null ? MappoDeploymentScope.resource_group : deploymentScope,
            deploymentSettingMode(executionSettings),
            deploymentSettingBoolean(executionSettings == null ? null : executionSettings.whatIfOnCanary(), false),
            deploymentSettingBoolean(executionSettings == null ? null : executionSettings.verifyAfterDeploy(), true),
            sanitizeStringMap(parameterDefaults),
            normalize(releaseNotes),
            sanitizeList(verificationHints)
        );
    }

    private static Map<String, String> sanitizeStringMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            if (key.isBlank()) {
                continue;
            }
            out.put(key, normalize(entry.getValue()));
        }
        return out;
    }

    private static List<String> sanitizeList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String value : source) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private static MappoArmDeploymentMode deploymentSettingMode(ReleaseExecutionSettingsRequest settings) {
        if (settings == null || settings.armMode() == null) {
            return MappoArmDeploymentMode.incremental;
        }
        return settings.armMode();
    }

    private static boolean deploymentSettingBoolean(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
