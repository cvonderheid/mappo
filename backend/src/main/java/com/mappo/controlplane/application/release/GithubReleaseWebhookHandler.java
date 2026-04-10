package com.mappo.controlplane.application.release;

import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;

public interface GithubReleaseWebhookHandler {

    ReleaseManifestIngestResultRecord handle(
        String endpointId,
        String rawPayload,
        String githubEvent,
        String signatureHeader,
        String githubDeliveryId
    );
}
