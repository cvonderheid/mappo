package com.mappo.controlplane.domain.execution;

import com.mappo.controlplane.model.RunPreviewMode;

public record DeploymentDriverCapabilities(
    boolean supportsPreview,
    RunPreviewMode previewMode,
    boolean supportsExternalExecutionHandle,
    boolean supportsExternalLogs,
    boolean supportsCancel
) {

    public static DeploymentDriverCapabilities defaults() {
        return new DeploymentDriverCapabilities(false, RunPreviewMode.UNSUPPORTED, false, false, false);
    }
}
