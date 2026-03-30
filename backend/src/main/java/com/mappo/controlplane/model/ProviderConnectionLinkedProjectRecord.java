package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProviderConnectionLinkedProjectRecord(
    @Schema(description = "Project id linked to this provider connection.")
    String projectId,
    @Schema(description = "Project display name linked to this provider connection.")
    String projectName
) {
}
