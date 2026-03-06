package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseExecutionSettingsRequest(
    MappoArmDeploymentMode armMode,
    Boolean whatIfOnCanary,
    Boolean verifyAfterDeploy
) {
}
