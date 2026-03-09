package com.mappo.controlplane.model.query;

import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;

public record ReleaseWebhookDeliveryPageQuery(
    Integer page,
    Integer size,
    String deliveryId,
    MappoReleaseWebhookStatus status
) {
}
