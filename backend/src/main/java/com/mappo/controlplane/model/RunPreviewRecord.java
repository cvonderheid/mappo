package com.mappo.controlplane.model;

import java.util.List;

public record RunPreviewRecord(
    String releaseId,
    String releaseVersion,
    RunPreviewMode mode,
    String caveat,
    List<String> warnings,
    List<RunTargetPreviewRecord> targets
) {
}
