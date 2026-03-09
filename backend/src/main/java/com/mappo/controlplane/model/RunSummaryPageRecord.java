package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RunSummaryPageRecord(
    @Schema(description = "Deployment runs for the current page.", requiredMode = Schema.RequiredMode.REQUIRED)
    List<RunSummaryRecord> items,
    @Schema(description = "Pagination metadata for the current runs page.", requiredMode = Schema.RequiredMode.REQUIRED)
    PageMetadataRecord page,
    @Schema(description = "Number of runs currently in the running state across the full filtered result set.", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer activeRunCount
) {
}
