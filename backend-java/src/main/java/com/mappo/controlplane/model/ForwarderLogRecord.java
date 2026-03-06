package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ForwarderLogRecord(
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
    ForwarderLogDetailsRecord details,
    OffsetDateTime createdAt
) {
}
