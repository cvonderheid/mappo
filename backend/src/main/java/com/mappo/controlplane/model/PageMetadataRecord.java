package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record PageMetadataRecord(
    @Schema(description = "Zero-based page index returned by the API.", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer page,
    @Schema(description = "Page size used for this response.", example = "25", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer size,
    @Schema(description = "Total number of matching records across all pages.", example = "132", requiredMode = Schema.RequiredMode.REQUIRED)
    Long totalItems,
    @Schema(description = "Total number of pages available for the current filter set.", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer totalPages
) {
}
