package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReleaseIngestLinkedProjectRecord(
    @Schema(description = "Linked project id.", example = "azure-managed-app-deployment-stack")
    String projectId,
    @Schema(description = "Linked project display name.", example = "Azure Managed App Deployment Stack")
    String projectName
) {
}
