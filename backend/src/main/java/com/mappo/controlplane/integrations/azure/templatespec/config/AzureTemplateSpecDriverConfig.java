package com.mappo.controlplane.integrations.azure.templatespec.config;

import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;

public record AzureTemplateSpecDriverConfig(
    boolean supportsPreview,
    boolean supportsExternalExecutionHandle
) implements ProjectDeploymentDriverConfig {

    public static AzureTemplateSpecDriverConfig defaults() {
        return new AzureTemplateSpecDriverConfig(false, false);
    }
}
