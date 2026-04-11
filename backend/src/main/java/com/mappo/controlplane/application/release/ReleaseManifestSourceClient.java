package com.mappo.controlplane.application.release;

import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;

public interface ReleaseManifestSourceClient {

    ReleaseIngestProviderType provider();

    String fetchManifest(String repo, String path, String ref);
}
