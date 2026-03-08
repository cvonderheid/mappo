package com.mappo.controlplane.model;

import java.util.List;

public record RunPreviewChangeRecord(
    String resourceId,
    String changeType,
    String unsupportedReason,
    List<RunPreviewPropertyChangeRecord> propertyChanges
) {
}
