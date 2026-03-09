package com.mappo.controlplane.model;

import java.util.List;

public record MarketplaceEventPageRecord(
    List<MarketplaceEventRecord> items,
    PageMetadataRecord page
) {
}
