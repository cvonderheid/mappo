package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseIngestEndpointCreateRequest(
    @NotBlank String name,
    @NotNull ReleaseIngestProviderType provider,
    Boolean enabled,
    String secretRef,
    String repoFilter,
    String branchFilter,
    String pipelineIdFilter,
    String manifestPath
) {
}
