package com.mappo.controlplane.integrations.azuredevops.pipeline.config;

import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;

public record PipelineTriggerDriverConfig(
    String pipelineSystem,
    String organization,
    String project,
    String repository,
    String pipelineId,
    String branch,
    String azureServiceConnectionName,
    boolean supportsExternalExecutionHandle,
    boolean supportsExternalLogs
) implements ProjectDeploymentDriverConfig {

    public static PipelineTriggerDriverConfig defaults() {
        return new PipelineTriggerDriverConfig(
            "azure_devops",
            "",
            "",
            "",
            "",
            "main",
            "",
            true,
            true
        );
    }
}
