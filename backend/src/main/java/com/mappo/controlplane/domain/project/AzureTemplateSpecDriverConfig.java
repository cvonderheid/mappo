package com.mappo.controlplane.domain.project;

public record AzureTemplateSpecDriverConfig(
    boolean supportsPreview,
    boolean supportsExternalExecutionHandle
) implements ProjectDeploymentDriverConfig {

    public static AzureTemplateSpecDriverConfig defaults() {
        return new AzureTemplateSpecDriverConfig(false, false);
    }
}
