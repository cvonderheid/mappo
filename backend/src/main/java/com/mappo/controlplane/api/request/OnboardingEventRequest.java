package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.model.MarketplaceEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
    OnboardingEventMetadataRequest metadata,
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

    public String registrationSource() {
        if (metadata == null) {
            return null;
        }
        return nullable(metadata.source());
    }

    public String marketplacePayloadId() {
        if (metadata == null) {
            return null;
        }
        return nullable(metadata.marketplacePayloadId());
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
