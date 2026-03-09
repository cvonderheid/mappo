package com.mappo.controlplane.model.query;

public record ReleaseWebhookDeliveryPageQuery(
    Integer page,
    Integer size,
    String deliveryId,
    String status
) {
}
