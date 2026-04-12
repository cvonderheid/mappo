package com.mappo.tooling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TargetInventoryPayloadFactory {

    private static final TypeReference<List<LinkedHashMap<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final String commandName;

    TargetInventoryPayloadFactory(ObjectMapper objectMapper, String commandName) {
        this.objectMapper = objectMapper;
        this.commandName = commandName;
    }

    List<LinkedHashMap<String, Object>> readInventory(Path inventoryFile) {
        try {
            return objectMapper.readValue(FileSupport.readText(inventoryFile), LIST_OF_MAPS);
        } catch (Exception exception) {
            throw new ToolingException(commandName + ": inventory JSON must be an array", 2);
        }
    }

    Map<String, Object> readMap(String body) {
        try {
            return objectMapper.readValue(body, MAP_TYPE);
        } catch (Exception exception) {
            throw new ToolingException(commandName + ": API returned invalid JSON: " + exception.getMessage(), 1);
        }
    }

    String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new ToolingException(commandName + ": failed serializing payload: " + exception.getMessage(), 1);
        }
    }

    Map<String, Object> buildOnboardingPayload(Map<String, Object> row, PayloadOptions options) {
        String targetId = stringValue(row.get("id"));
        Map<String, String> tags = stringMap(mapValue(row.get("tags")));
        Map<String, Object> metadata = mapValue(row.get("metadata"));
        Map<String, String> executionConfig = stringMap(mapValue(metadata.get("execution_config")));
        String projectId = firstNonBlank(
            options.projectIdOverride(),
            stringValue(row.get("project_id")),
            stringValue(metadata.get("project_id"))
        );
        String managedApplicationId = firstNonBlank(stringValue(row.get("managed_app_id")), stringValue(metadata.get("managed_application_id")));
        String managedResourceGroupId = stringValue(metadata.get("managed_resource_group_id"));
        String containerAppResourceId = firstNonBlank(stringValue(row.get("container_app_resource_id")), stringValue(metadata.get("container_app_resource_id")));
        String customerName = firstNonBlank(tags.get("customer"), stringValue(metadata.get("customer_name")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", options.eventId());
        payload.put("eventType", options.eventType());
        payload.put("tenantId", stringValue(row.get("tenant_id")));
        payload.put("subscriptionId", stringValue(row.get("subscription_id")));
        payload.put("targetId", targetId);
        payload.put("displayName", firstNonBlank(stringValue(metadata.get("display_name")), stringValue(metadata.get("managed_application_name")), targetId));
        if (!projectId.isBlank()) {
            payload.put("projectId", projectId);
        }
        payload.put("targetGroup", firstNonBlank(tags.get("ring"), "prod"));
        payload.put("environment", firstNonBlank(tags.get("environment"), "prod"));
        payload.put("tier", firstNonBlank(tags.get("tier"), "standard"));
        payload.put("healthStatus", "registered");
        payload.put("lastDeployedRelease", "unknown");
        if (!managedApplicationId.isBlank()) {
            payload.put("managedApplicationId", managedApplicationId);
        }
        if (!managedResourceGroupId.isBlank()) {
            payload.put("managedResourceGroupId", managedResourceGroupId);
        }
        if (!containerAppResourceId.isBlank()) {
            payload.put("containerAppResourceId", containerAppResourceId);
        }
        if (!stringValue(metadata.get("container_app_name")).isBlank()) {
            payload.put("containerAppName", stringValue(metadata.get("container_app_name")));
        }
        if (!customerName.isBlank()) {
            payload.put("customerName", customerName);
        }
        if (!stringValue(tags.get("region")).isBlank()) {
            payload.put("region", tags.get("region"));
        }
        payload.put("tags", tags);
        payload.put(
            "metadata",
            buildMetadataPayload(options.sourceLabel(), options.eventId(), targetId, managedApplicationId, managedResourceGroupId, executionConfig)
        );
        return payload;
    }

    List<String> missingInventoryFields(Map<String, Object> row) {
        List<String> missing = new ArrayList<>();
        if (stringValue(row.get("id")).isBlank()) {
            missing.add("id");
        }
        if (stringValue(row.get("tenant_id")).isBlank()) {
            missing.add("tenant_id");
        }
        if (stringValue(row.get("subscription_id")).isBlank()) {
            missing.add("subscription_id");
        }
        return missing;
    }

    String targetId(Map<String, Object> row) {
        return stringValue(row.get("id"));
    }

    private Map<String, Object> buildMetadataPayload(
        String sourceLabel,
        String payloadEventId,
        String targetId,
        String managedApplicationId,
        String managedResourceGroupId,
        Map<String, String> executionConfig
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", sourceLabel);
        metadata.put("marketplacePayloadId", payloadEventId);
        metadata.put("targetImportPayloadId", payloadEventId);
        metadata.put("inventoryTargetId", targetId);
        if (!managedApplicationId.isBlank()) {
            metadata.put("inventoryManagedApplicationId", managedApplicationId);
        }
        if (!managedResourceGroupId.isBlank()) {
            metadata.put("inventoryManagedResourceGroupId", managedResourceGroupId);
        }
        if (!executionConfig.isEmpty()) {
            metadata.put("executionConfig", executionConfig);
        }
        return metadata;
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> out = new LinkedHashMap<>();
            source.forEach((key, item) -> {
                String normalizedKey = stringValue(key);
                if (!normalizedKey.isBlank()) {
                    out.put(normalizedKey, item);
                }
            });
            return out;
        }
        return Map.of();
    }

    private Map<String, String> stringMap(Map<String, Object> source) {
        Map<String, String> out = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = stringValue(key);
            String normalizedValue = stringValue(value);
            if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                out.put(normalizedKey, normalizedValue);
            }
        });
        return out;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    record PayloadOptions(String eventType, String eventId, String sourceLabel, String projectIdOverride) {
    }
}
