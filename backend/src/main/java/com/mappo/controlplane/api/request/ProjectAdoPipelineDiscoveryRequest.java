package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectAdoPipelineDiscoveryRequest(
    String organization,
    String project,
    String providerConnectionId,
    String nameContains
) {
}
