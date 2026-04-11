package com.mappo.controlplane.integrations.azuredevops.pipeline;

public record AzureDevOpsRepositoryDefinitionRecord(
    String id,
    String name,
    String defaultBranch,
    String webUrl,
    String remoteUrl
) {
}
