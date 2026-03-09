package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MarketplaceEventPageRecord(
    @Schema(description = "Marketplace onboarding events for the current page.", requiredMode = Schema.RequiredMode.REQUIRED)
    List<MarketplaceEventRecord> items,
    @Schema(description = "Pagination metadata for the current onboarding-events page.", requiredMode = Schema.RequiredMode.REQUIRED)
    PageMetadataRecord page
) {
}
