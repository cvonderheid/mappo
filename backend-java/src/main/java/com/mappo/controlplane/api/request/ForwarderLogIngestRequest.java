package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.command.ForwarderLogIngestCommand;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForwarderLogIngestRequest(
    @NotBlank String logId,
    MappoForwarderLogLevel level,
    @NotBlank String message,
    String eventId,
    MarketplaceEventType eventType,
    String targetId,
    UUID tenantId,
    UUID subscriptionId,
    String functionAppName,
    String forwarderRequestId,
    Integer backendStatusCode,
    Map<String, Object> details,
    OffsetDateTime occurredAt
) {

    public ForwarderLogIngestCommand toCommand() {
        return new ForwarderLogIngestCommand(
            normalize(logId),
            level == null ? MappoForwarderLogLevel.error : level,
            normalize(message),
            nullable(eventId),
            eventType,
            nullable(targetId),
            tenantId,
            subscriptionId,
            nullable(functionAppName),
            nullable(forwarderRequestId),
            backendStatusCode,
            nullable(detailValue(details)),
            nullable(backendResponseValue(details)),
            occurredAt
        );
    }

    private static Map<String, Object> sanitizeDetails(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            if (!key.isBlank()) {
                out.put(key, entry.getValue());
            }
        }
        return out;
    }

    private static String detailValue(Map<String, Object> source) {
        return source == null ? null : normalize(source.get("detail"));
    }

    private static String backendResponseValue(Map<String, Object> source) {
        return source == null ? null : normalize(source.get("backend_response"));
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
