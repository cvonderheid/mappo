package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OnboardingEventMetadataRequest(
    String source,
    String marketplacePayloadId
) {
}
