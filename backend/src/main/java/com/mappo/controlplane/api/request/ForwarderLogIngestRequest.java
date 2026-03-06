package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.command.ForwarderLogIngestCommand;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
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
    ForwarderLogDetailsRequest details,
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
            details == null ? null : nullable(details.detail()),
            details == null ? null : nullable(details.backendResponse()),
            occurredAt
        );
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
