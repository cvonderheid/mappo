package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Typed runtime-health-provider configuration. The selected runtimeHealthProvider determines how the fields are interpreted.")
public record ProjectRuntimeHealthProviderConfigRequest(
    String path,
    Integer expectedStatus,
    Long timeoutMs
) {
}
