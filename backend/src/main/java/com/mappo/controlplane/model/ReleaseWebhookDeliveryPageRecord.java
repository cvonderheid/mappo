package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ReleaseWebhookDeliveryPageRecord(
    @Schema(description = "GitHub release-webhook deliveries for the current page.", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ReleaseWebhookDeliveryRecord> items,
    @Schema(description = "Pagination metadata for the current release-webhook page.", requiredMode = Schema.RequiredMode.REQUIRED)
    PageMetadataRecord page
) {
}
