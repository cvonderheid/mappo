package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectAdoServiceConnectionDiscoveryRequest(
    String organization,
    String project,
    String personalAccessTokenRef,
    String nameContains
) {
}
