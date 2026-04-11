package com.mappo.controlplane.application.release;

public record ReleaseWebhookRequest(
    String endpointId,
    String rawPayload,
    String eventTypeHeader,
    String deliveryIdHeader,
    String signatureHeader,
    String authorizationHeader,
    String queryToken,
    String projectId
) {
}
