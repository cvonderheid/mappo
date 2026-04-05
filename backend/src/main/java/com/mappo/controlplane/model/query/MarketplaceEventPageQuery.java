package com.mappo.controlplane.model.query;

import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;

public record MarketplaceEventPageQuery(
    Integer page,
    Integer size,
    String projectId,
    String eventId,
    MappoMarketplaceEventStatus status
) {
}
