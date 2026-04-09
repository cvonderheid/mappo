package com.mappo.controlplane.integrations.azure.deploymentstack.config;

import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;

public record AzureDeploymentStackDriverConfig(
    boolean supportsPreview,
    String previewMode,
    boolean supportsExternalExecutionHandle
) implements ProjectDeploymentDriverConfig {

    public static AzureDeploymentStackDriverConfig defaults() {
        return new AzureDeploymentStackDriverConfig(true, "ARM_WHAT_IF", false);
    }
}
