package com.mappo.controlplane.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReleaseExecutionSettingsRecord(
    MappoArmDeploymentMode armMode,
    boolean whatIfOnCanary,
    boolean verifyAfterDeploy
) {
}
