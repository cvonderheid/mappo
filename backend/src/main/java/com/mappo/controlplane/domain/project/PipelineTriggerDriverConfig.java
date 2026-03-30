package com.mappo.controlplane.domain.project;

public record PipelineTriggerDriverConfig(
    String pipelineSystem,
    String organization,
    String project,
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
            "main",
            "",
            true,
            true
        );
    }
}
