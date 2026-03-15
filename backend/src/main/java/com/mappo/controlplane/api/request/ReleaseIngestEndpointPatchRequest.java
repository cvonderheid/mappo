package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseIngestEndpointPatchRequest(
    String name,
    ReleaseIngestProviderType provider,
    Boolean enabled,
    String secretRef,
    String repoFilter,
    String branchFilter,
    String pipelineIdFilter,
    String manifestPath,
    Map<String, Object> sourceConfig
) {
}
