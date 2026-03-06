package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;

public record ForwarderLogIngestResultRecord(
    String logId,
    MappoMarketplaceEventStatus status,
    String message
) {
}
