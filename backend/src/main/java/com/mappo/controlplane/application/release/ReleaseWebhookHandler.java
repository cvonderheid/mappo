package com.mappo.controlplane.application.release;

import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;

public interface ReleaseWebhookHandler {

    ReleaseIngestProviderType provider();

    ReleaseManifestIngestResultRecord handle(ReleaseWebhookRequest request);
}
