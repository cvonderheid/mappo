package com.mappo.controlplane.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TargetRegistrationMetadataRecord(
    String containerAppName,
    String source
) {
}
