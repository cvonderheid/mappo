package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record MarketplaceEventRecord(
    String eventId,
    MarketplaceEventType eventType,
    MappoMarketplaceEventStatus status,
    String message,
    String targetId,
    UUID tenantId,
    UUID subscriptionId,
    Map<String, Object> payload,
    OffsetDateTime createdAt,
    OffsetDateTime processedAt
) {
}
