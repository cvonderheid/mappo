package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectAdoBranchDiscoveryRequest(
    String organization,
    String project,
    String providerConnectionId,
    String repositoryId,
    String repository,
    String nameContains
) {
}
