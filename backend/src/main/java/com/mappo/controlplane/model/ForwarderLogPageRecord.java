package com.mappo.controlplane.model;

import java.util.List;

public record ForwarderLogPageRecord(
    List<ForwarderLogRecord> items,
    PageMetadataRecord page
) {
}
