package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.model.command.TargetRegistrationPatchCommand;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TargetRegistrationPatchRequest(
    String displayName,
    String managedApplicationId,
    String managedResourceGroupId,
    String customerName,
    String lastDeployedRelease,
    MappoHealthStatus healthStatus,
    String containerAppResourceId,
    Map<String, String> tags,
    Map<String, Object> metadata
) {

    public TargetRegistrationPatchCommand toCommand() {
        return new TargetRegistrationPatchCommand(
            nullable(displayName),
            nullable(customerName),
            nullable(managedApplicationId),
            nullable(managedResourceGroupId),
            nullable(containerAppResourceId),
            metadataString("container_app_name"),
            metadataString("source"),
            nullable(lastDeployedRelease),
            healthStatus,
            sanitizeTags(tags)
        );
    }

    private static Map<String, String> sanitizeTags(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            String value = normalize(entry.getValue());
            if (!key.isBlank()) {
                out.put(key, value);
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

    private String metadataString(String key) {
        Object value = metadata == null ? null : metadata.get(key);
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
