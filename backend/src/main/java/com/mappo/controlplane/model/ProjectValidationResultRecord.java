package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record ProjectValidationResultRecord(
    @Schema(description = "Project id validated by this request.", example = "azure-appservice-ado-pipeline")
    String projectId,
    @Schema(description = "Overall validation status.", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean valid,
    @Schema(description = "Timestamp when validation completed (UTC).")
    OffsetDateTime validatedAt,
    @Schema(description = "Detailed validation findings.")
    List<ProjectValidationFindingRecord> findings
) {
}

