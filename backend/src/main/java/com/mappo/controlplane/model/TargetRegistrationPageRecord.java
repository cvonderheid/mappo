package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TargetRegistrationPageRecord(
    @Schema(description = "Registered targets for the current admin page.", requiredMode = Schema.RequiredMode.REQUIRED)
    List<TargetRegistrationRecord> items,
    @Schema(description = "Pagination metadata for the current registrations page.", requiredMode = Schema.RequiredMode.REQUIRED)
    PageMetadataRecord page
) {
}
