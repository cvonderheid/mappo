package com.mappo.controlplane.model;

import java.util.List;

public record ReleaseWebhookDeliveryPageRecord(
    List<ReleaseWebhookDeliveryRecord> items,
    PageMetadataRecord page
) {
}
