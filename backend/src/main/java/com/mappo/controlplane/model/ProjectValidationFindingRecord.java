package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectValidationFindingRecord(
    @Schema(description = "Validation scope category.")
    ProjectValidationScope scope,
    @Schema(description = "Validation outcome status.")
    ProjectValidationFindingStatus status,
    @Schema(description = "Machine-readable finding code.", example = "AZURE_CREDENTIALS_MISSING")
    String code,
    @Schema(description = "Human-readable finding message.")
    String message
) {
}

