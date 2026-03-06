package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.model.MarketplaceEventType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ForwarderLogIngestCommand(
    String logId,
    MappoForwarderLogLevel level,
    String message,
    String eventId,
    MarketplaceEventType eventType,
    String targetId,
    UUID tenantId,
    UUID subscriptionId,
    String functionAppName,
    String forwarderRequestId,
    Integer backendStatusCode,
    String detailText,
    String backendResponseBody,
    OffsetDateTime occurredAt
) {
}
