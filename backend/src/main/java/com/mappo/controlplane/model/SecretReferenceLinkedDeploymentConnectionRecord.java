package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record SecretReferenceLinkedDeploymentConnectionRecord(
    @Schema(description = "Deployment connection id.")
    String id,
    @Schema(description = "Deployment connection display name.")
    String name
) {
}
