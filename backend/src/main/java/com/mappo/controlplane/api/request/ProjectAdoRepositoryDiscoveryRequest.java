package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectAdoRepositoryDiscoveryRequest(
    String organization,
    String project,
    String providerConnectionId,
    String nameContains
) {
}
