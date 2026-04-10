package com.mappo.controlplane.application.release;

import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;

public interface AzureDevOpsReleaseWebhookHandler {

    ReleaseManifestIngestResultRecord handle(
        String rawPayload,
        String eventTypeHeader,
        String deliveryIdHeader,
        String authorizationHeader,
        String queryToken,
        String projectId
    );

    ReleaseManifestIngestResultRecord handle(
        String endpointId,
        String rawPayload,
        String eventTypeHeader,
        String deliveryIdHeader,
        String authorizationHeader,
        String queryToken,
        String projectId
    );
}
