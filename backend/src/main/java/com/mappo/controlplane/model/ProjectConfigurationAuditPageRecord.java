package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ProjectConfigurationAuditPageRecord(
    @Schema(description = "Project configuration audit events for the current page.", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ProjectConfigurationAuditRecord> items,
    @Schema(description = "Pagination metadata for the audit event page.", requiredMode = Schema.RequiredMode.REQUIRED)
    PageMetadataRecord page
) {
}

