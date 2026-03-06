package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForwarderLogDetailsRequest(
    String detail,
    String backendResponse
) {
}
