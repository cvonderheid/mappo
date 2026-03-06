package com.mappo.controlplane.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForwarderLogDetailsRecord(
    String detail,
    String backendResponse
) {
}
