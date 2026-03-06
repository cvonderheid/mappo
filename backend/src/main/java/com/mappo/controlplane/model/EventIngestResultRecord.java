package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;

public record EventIngestResultRecord(
    String eventId,
    MappoMarketplaceEventStatus status,
    String message,
    String targetId
) {
}
