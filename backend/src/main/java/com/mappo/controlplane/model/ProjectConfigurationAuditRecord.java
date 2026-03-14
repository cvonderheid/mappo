package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;

public record ProjectConfigurationAuditRecord(
    @Schema(description = "Audit event id.", example = "pca-c2f9fbc5ad")
    String id,
    @Schema(description = "Project id associated with this audit event.", example = "azure-appservice-ado-pipeline")
    String projectId,
    @Schema(description = "Action type for the recorded configuration mutation.")
    ProjectConfigurationAuditAction action,
    @Schema(description = "Actor label associated with this change.", example = "api")
    String actor,
    @Schema(description = "Human-readable change summary.", example = "Updated project configuration.")
    String changeSummary,
    @Schema(description = "Configuration snapshot before mutation.")
    Map<String, Object> beforeSnapshot,
    @Schema(description = "Configuration snapshot after mutation.")
    Map<String, Object> afterSnapshot,
    @Schema(description = "Audit event timestamp (UTC).")
    OffsetDateTime createdAt
) {
}

