package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProviderConnectionLinkedProjectRecord(
    @Schema(description = "Project id using this deployment connection.")
    String projectId,
    @Schema(description = "Project display name using this deployment connection.")
    String projectName
) {
}
