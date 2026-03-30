package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;

public record ReleaseIngestEndpointMutationRecord(
    String id,
    String name,
    ReleaseIngestProviderType provider,
    boolean enabled,
    String secretRef,
    String repoFilter,
    String branchFilter,
    String pipelineIdFilter,
    String manifestPath
) {
}
