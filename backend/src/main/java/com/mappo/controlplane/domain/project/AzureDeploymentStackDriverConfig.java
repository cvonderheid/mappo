package com.mappo.controlplane.domain.project;

public record AzureDeploymentStackDriverConfig(
    boolean supportsPreview,
    String previewMode,
    boolean supportsExternalExecutionHandle
) implements ProjectDeploymentDriverConfig {

    public static AzureDeploymentStackDriverConfig defaults() {
        return new AzureDeploymentStackDriverConfig(true, "ARM_WHAT_IF", false);
    }
}
