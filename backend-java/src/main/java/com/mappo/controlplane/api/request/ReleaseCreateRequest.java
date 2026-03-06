package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.model.command.CreateReleaseCommand;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseCreateRequest(
    @NotBlank String templateSpecId,
    @NotBlank String templateSpecVersion,
    MappoDeploymentMode deploymentMode,
    String templateSpecVersionId,
    MappoDeploymentScope deploymentScope,
    Map<String, Object> deploymentModeSettings,
    Map<String, String> parameterDefaults,
    String releaseNotes,
    List<String> verificationHints
) {

    public CreateReleaseCommand toCommand() {
        return new CreateReleaseCommand(
            normalize(templateSpecId),
            normalize(templateSpecVersion),
            deploymentMode == null ? MappoDeploymentMode.container_patch : deploymentMode,
            nullable(templateSpecVersionId),
            deploymentScope == null ? MappoDeploymentScope.resource_group : deploymentScope,
            deploymentSettingMode(deploymentModeSettings),
            deploymentSettingBoolean(deploymentModeSettings, "what_if_on_canary", false),
            deploymentSettingBoolean(deploymentModeSettings, "verify_after_deploy", true),
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

    private static Map<String, Object> sanitizeObjectMap(Map<String, Object> source) {
        return source == null ? Map.of() : source;
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

    private static MappoArmDeploymentMode deploymentSettingMode(Map<String, Object> settings) {
        String value = deploymentSettingText(settings, "arm_mode");
        MappoArmDeploymentMode parsed = MappoArmDeploymentMode.lookupLiteral(value);
        return parsed == null ? MappoArmDeploymentMode.incremental : parsed;
    }

    private static boolean deploymentSettingBoolean(
        Map<String, Object> settings,
        String key,
        boolean fallback
    ) {
        if (settings == null || settings.isEmpty()) {
            return fallback;
        }
        Object value = settings.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(normalized);
    }

    private static String deploymentSettingText(Map<String, Object> settings, String key) {
        if (settings == null || settings.isEmpty()) {
            return "";
        }
        return normalize(settings.get(key)).toLowerCase();
    }
}
