package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ForwarderLogPageRecord(
    @Schema(description = "Marketplace forwarder logs for the current page.", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ForwarderLogRecord> items,
    @Schema(description = "Pagination metadata for the current forwarder-log page.", requiredMode = Schema.RequiredMode.REQUIRED)
    PageMetadataRecord page
) {
}
