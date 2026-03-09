package com.mappo.controlplane.model.query;

public record MarketplaceEventPageQuery(
    Integer page,
    Integer size,
    String eventId,
    String status
) {
}
