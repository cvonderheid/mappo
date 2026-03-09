package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TargetPageRecord(
    @Schema(description = "Fleet targets for the current page.", requiredMode = Schema.RequiredMode.REQUIRED)
    List<TargetRecord> items,
    @Schema(description = "Pagination metadata for the current fleet page.", requiredMode = Schema.RequiredMode.REQUIRED)
    PageMetadataRecord page
) {
}
