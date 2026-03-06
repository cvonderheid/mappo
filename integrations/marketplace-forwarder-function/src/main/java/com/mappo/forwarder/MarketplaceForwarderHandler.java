package com.mappo.forwarder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class MarketplaceForwarderHandler {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final BackendPoster backendPoster;
    private final Map<String, String> environment;
    private final Logger logger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    MarketplaceForwarderHandler(BackendPoster backendPoster, Map<String, String> environment, Logger logger) {
        this.backendPoster = backendPoster;
        this.environment = environment;
        this.logger = logger;
    }

    ForwarderResponse handle(String rawBody, Map<String, String> requestHeaders) {
        String requestId = firstNonBlank(
            requestHeaders.get("x-ms-request-id"),
            requestHeaders.get("x-request-id")
        );

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            emitForwarderLog("warning", "Forwarder received invalid JSON payload.", null, null, null, "Invalid JSON payload.", requestId);
            return jsonResponse(400, Map.of("detail", "Invalid JSON payload."));
        }

        if (payload == null || !payload.isObject()) {
            emitForwarderLog("warning", "Forwarder received non-object JSON payload.", null, null, null, "Payload must be a JSON object.", requestId);
            return jsonResponse(400, Map.of("detail", "Payload must be a JSON object."));
        }

        Map<String, Object> normalizedEvent;
        try {
            normalizedEvent = buildNormalizedEvent(payload);
        } catch (IllegalArgumentException exception) {
            emitForwarderLog("warning", "Forwarder could not normalize marketplace payload.", null, null, null, exception.getMessage(), requestId);
            return jsonResponse(400, Map.of("detail", exception.getMessage()));
        }

        try {
            HttpPostResult result = forwardToMappo(normalizedEvent);
            if (result.statusCode() >= 400) {
                emitForwarderLog(
                    "error",
                    "MAPPO backend rejected forwarded marketplace event.",
                    normalizedEvent,
                    result.statusCode(),
                    result.body(),
                    null,
                    requestId
                );
            }
            return new ForwarderResponse(result.statusCode(), result.body());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to forward marketplace event to MAPPO backend.", exception);
            emitForwarderLog(
                "error",
                "Forwarder request to MAPPO backend failed.",
                normalizedEvent,
                null,
                null,
                exception.getMessage(),
                requestId
            );
            return jsonResponse(502, Map.of("detail", "Forwarding failure: " + exception.getMessage()));
        }
    }

    private Map<String, Object> buildNormalizedEvent(JsonNode payload) {
        if (hasDirectOnboardingShape(payload)) {
            return objectMapper.convertValue(payload, MAP_TYPE);
        }

        JsonNode target = objectNode(payload, "mappoTarget", "mappo_target");
        if (target == null || target.isMissingNode() || !target.isObject() || target.isEmpty()) {
            target = objectNode(payload, "target");
        }
        if (target == null || target.isMissingNode() || !target.isObject()) {
            target = objectMapper.createObjectNode();
        }

        String eventId = firstNonBlank(
            stringValue(payload, "eventId", "event_id"),
            stringValue(payload, "id"),
            "evt-marketplace-" + Instant.now().toEpochMilli()
        );
        String eventType = firstNonBlank(
            stringValue(payload, "eventType", "event_type"),
            stringValue(payload, "action"),
            "subscription_purchased"
        );

        String tenantId = stringValue(target, "tenantId", "tenant_id");
        String subscriptionId = stringValue(target, "subscriptionId", "subscription_id");
        String containerAppResourceId = stringValue(target, "containerAppResourceId", "container_app_resource_id");

        if (tenantId.isBlank() || subscriptionId.isBlank() || containerAppResourceId.isBlank()) {
            StringBuilder missing = new StringBuilder();
            appendMissing(missing, tenantId.isBlank(), "tenant_id");
            appendMissing(missing, subscriptionId.isBlank(), "subscription_id");
            appendMissing(missing, containerAppResourceId.isBlank(), "container_app_resource_id");
            throw new IllegalArgumentException(
                "Payload cannot be normalized. Provide onboarding shape directly or include `mappo_target` with required fields: "
                    + missing
            );
        }

        Map<String, Object> metadata = new LinkedHashMap<>(objectMap(target, "metadata"));
        metadata.put("source", "function-marketplace-forwarder");
        metadata.put("marketplacePayloadId", stringValue(payload, "id"));

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("eventId", eventId);
        normalized.put("eventType", eventType);
        normalized.put("tenantId", tenantId);
        normalized.put("subscriptionId", subscriptionId);
        putIfPresent(normalized, "targetId", firstNonBlank(stringValue(target, "targetId", "target_id"), stringValue(target, "id")));
        putIfPresent(normalized, "displayName", stringValue(target, "displayName", "display_name"));
        putIfPresent(normalized, "managedApplicationId", stringValue(target, "managedApplicationId", "managed_application_id"));
        putIfPresent(normalized, "managedResourceGroupId", stringValue(target, "managedResourceGroupId", "managed_resource_group_id"));
        normalized.put("containerAppResourceId", containerAppResourceId);
        putIfPresent(normalized, "containerAppName", stringValue(target, "containerAppName", "container_app_name"));
        putIfPresent(normalized, "customerName", stringValue(target, "customerName", "customer_name"));
        normalized.put("targetGroup", firstNonBlank(stringValue(target, "targetGroup", "target_group"), "prod"));
        normalized.put("region", firstNonBlank(stringValue(target, "region"), "eastus"));
        normalized.put("environment", firstNonBlank(stringValue(target, "environment"), "prod"));
        normalized.put("tier", firstNonBlank(stringValue(target, "tier"), "standard"));
        normalized.put("tags", stringMap(objectMap(target, "tags")));
        normalized.put("metadata", metadata);
        normalized.put("healthStatus", "registered");
        normalized.put("lastDeployedRelease", "unknown");
        return normalized;
    }

    private HttpPostResult forwardToMappo(Map<String, Object> eventPayload) {
        String endpoint = ingestEndpoint();
        double timeoutSeconds = timeoutSeconds();
        return backendPoster.postJson(endpoint, eventPayload, timeoutSeconds, ingestHeaders());
    }

    private void emitForwarderLog(
        String level,
        String message,
        Map<String, Object> normalizedEvent,
        Integer backendStatusCode,
        String responseBody,
        String detail,
        String requestId
    ) {
        String endpoint = forwarderLogsEndpoint();
        if (endpoint.isBlank()) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, "backendResponse", truncate(responseBody, 1600));
        putIfPresent(details, "detail", truncate(detail, 1600));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("logId", "fwd-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        payload.put("level", level);
        payload.put("message", message);
        putIfPresent(payload, "eventId", eventValue(normalizedEvent, "eventId"));
        putIfPresent(payload, "eventType", eventValue(normalizedEvent, "eventType"));
        putIfPresent(payload, "targetId", eventValue(normalizedEvent, "targetId"));
        putIfPresent(payload, "tenantId", eventValue(normalizedEvent, "tenantId"));
        putIfPresent(payload, "subscriptionId", eventValue(normalizedEvent, "subscriptionId"));
        putIfPresent(payload, "functionAppName", env("WEBSITE_SITE_NAME"));
        putIfPresent(payload, "forwarderRequestId", requestId);
        if (backendStatusCode != null) {
            payload.put("backendStatusCode", backendStatusCode);
        }
        payload.put("details", details);

        try {
            HttpPostResult result = backendPoster.postJson(endpoint, payload, timeoutSeconds(), ingestHeaders());
            if (result.statusCode() >= 400) {
                logger.warning("Forwarder log ingestion failed: status=" + result.statusCode() + " body=" + result.body());
            }
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Unable to emit forwarder log to MAPPO backend.", exception);
        }
    }

    private Map<String, String> ingestHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "mappo-marketplace-forwarder/1.0");
        String ingestToken = env("MAPPO_INGEST_TOKEN");
        if (!ingestToken.isBlank()) {
            headers.put("x-mappo-ingest-token", ingestToken);
        }
        return headers;
    }

    private String ingestEndpoint() {
        String endpoint = env("MAPPO_INGEST_ENDPOINT");
        if (!endpoint.isBlank()) {
            return endpoint;
        }
        String baseUrl = env("MAPPO_API_BASE_URL");
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("Missing MAPPO_INGEST_ENDPOINT or MAPPO_API_BASE_URL app setting.");
        }
        return baseUrl.replaceAll("/+$", "") + "/api/v1/admin/onboarding/events";
    }

    private String forwarderLogsEndpoint() {
        String endpoint = env("MAPPO_FORWARDER_LOGS_ENDPOINT");
        if (!endpoint.isBlank()) {
            return endpoint;
        }
        String baseUrl = env("MAPPO_API_BASE_URL");
        if (baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.replaceAll("/+$", "") + "/api/v1/admin/onboarding/forwarder-logs";
    }

    private double timeoutSeconds() {
        String value = env("MAPPO_INGEST_TIMEOUT_SECONDS");
        if (value.isBlank()) {
            return 15d;
        }
        return Double.parseDouble(value);
    }

    private ForwarderResponse jsonResponse(int statusCode, Map<String, String> payload) {
        try {
            return new ForwarderResponse(statusCode, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize response payload", exception);
        }
    }

    private boolean hasDirectOnboardingShape(JsonNode payload) {
        return hasNonBlank(payload, "eventId", "event_id")
            && hasNonBlank(payload, "tenantId", "tenant_id")
            && hasNonBlank(payload, "subscriptionId", "subscription_id")
            && hasNonBlank(payload, "containerAppResourceId", "container_app_resource_id");
    }

    private boolean hasNonBlank(JsonNode node, String... names) {
        return !stringValue(node, names).isBlank();
    }

    private String stringValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String normalized = value.asText("").trim();
                if (!normalized.isBlank()) {
                    return normalized;
                }
            }
        }
        return "";
    }

    private JsonNode objectNode(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode candidate = node.get(name);
            if (candidate != null && candidate.isObject()) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> objectMap(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        if (child != null && child.isObject()) {
            return objectMapper.convertValue(child, MAP_TYPE);
        }
        return Map.of();
    }

    private Map<String, String> stringMap(Map<String, Object> source) {
        Map<String, String> out = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.trim();
            String normalizedValue = value == null ? "" : String.valueOf(value).trim();
            if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                out.put(normalizedKey, normalizedValue);
            }
        });
        return out;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String normalized = String.valueOf(value).trim();
        if (!normalized.isBlank()) {
            target.put(key, normalized);
        }
    }

    private String eventValue(Map<String, Object> normalizedEvent, String key) {
        if (normalizedEvent == null) {
            return null;
        }
        Object value = normalizedEvent.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String env(String key) {
        String value = environment.getOrDefault(key, "");
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void appendMissing(StringBuilder builder, boolean missing, String fieldName) {
        if (!missing) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(", ");
        }
        builder.append(fieldName);
    }
}
