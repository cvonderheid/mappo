package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.model.MarketplaceEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OnboardingEventRequest(
    @NotBlank String eventId,
    MarketplaceEventType eventType,
    @NotNull UUID tenantId,
    @NotNull UUID subscriptionId,
    String targetId,
    String displayName,
    String managedApplicationId,
    String managedResourceGroupId,
    String containerAppResourceId,
    String containerAppName,
    String customerName,
    Map<String, String> tags,
    Map<String, Object> metadata,
    String targetGroup,
    String region,
    String environment,
    String tier,
    String lastDeployedRelease,
    MappoHealthStatus healthStatus
) {

    public MarketplaceEventType effectiveEventType() {
        if (eventType == null || eventType == MarketplaceEventType.UNKNOWN) {
            return MarketplaceEventType.SUBSCRIPTION_PURCHASED;
        }
        return eventType;
    }

    public Map<String, String> effectiveTags() {
        if (tags == null || tags.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = normalize(entry.getKey());
            String value = normalize(entry.getValue());
            if (!key.isBlank() && !value.isBlank()) {
                out.put(key, value);
            }
        }
        return out;
    }

    public Map<String, Object> effectiveMetadata() {
        if (metadata == null || metadata.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = normalize(entry.getKey());
            if (!key.isBlank()) {
                out.put(key, entry.getValue());
            }
        }
        return out;
    }

    public String registrationSource() {
        return metadataString("source");
    }

    public String marketplacePayloadId() {
        return metadataString("marketplace_payload_id");
    }

    public Map<String, Object> toPayloadMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "event_id", eventId);
        payload.put("event_type", effectiveEventType().literal());
        put(payload, "tenant_id", tenantId);
        put(payload, "subscription_id", subscriptionId);
        put(payload, "target_id", targetId);
        put(payload, "display_name", displayName);
        put(payload, "managed_application_id", managedApplicationId);
        put(payload, "managed_resource_group_id", managedResourceGroupId);
        put(payload, "container_app_resource_id", containerAppResourceId);
        put(payload, "container_app_name", containerAppName);
        put(payload, "customer_name", customerName);
        if (healthStatus != null) {
            payload.put("health_status", healthStatus.getLiteral());
        }
        put(payload, "last_deployed_release", lastDeployedRelease);
        put(payload, "target_group", targetGroup);
        put(payload, "region", region);
        put(payload, "environment", environment);
        put(payload, "tier", tier);
        if (!effectiveTags().isEmpty()) {
            payload.put("tags", effectiveTags());
        }
        if (!effectiveMetadata().isEmpty()) {
            payload.put("metadata", effectiveMetadata());
        }
        return payload;
    }

    private static void put(Map<String, Object> target, String key, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            target.put(key, normalized);
        }
    }

    private static void put(Map<String, Object> target, String key, UUID value) {
        if (value != null) {
            target.put(key, value.toString());
        }
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String metadataString(String key) {
        Object value = metadata == null ? null : metadata.get(key);
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
