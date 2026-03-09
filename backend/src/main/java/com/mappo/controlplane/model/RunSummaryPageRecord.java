package com.mappo.controlplane.model;

import java.util.List;

public record RunSummaryPageRecord(
    List<RunSummaryRecord> items,
    PageMetadataRecord page,
    Integer activeRunCount
) {
}
