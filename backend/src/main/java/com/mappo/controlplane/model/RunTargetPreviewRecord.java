package com.mappo.controlplane.model;

import java.util.List;

public record RunTargetPreviewRecord(
    String targetId,
    String displayName,
    String targetGroup,
    String managedResourceGroupId,
    RunPreviewTargetStatus status,
    String summary,
    List<String> warnings,
    StageErrorRecord error,
    List<RunPreviewChangeRecord> changes
) {
}
