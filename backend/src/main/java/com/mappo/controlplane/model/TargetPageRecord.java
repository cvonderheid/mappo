package com.mappo.controlplane.model;

import java.util.List;

public record TargetPageRecord(
    List<TargetRecord> items,
    PageMetadataRecord page
) {
}
