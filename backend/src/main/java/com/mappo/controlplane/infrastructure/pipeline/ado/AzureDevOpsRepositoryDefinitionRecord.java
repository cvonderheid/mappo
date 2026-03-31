package com.mappo.controlplane.infrastructure.pipeline.ado;

public record AzureDevOpsRepositoryDefinitionRecord(
    String id,
    String name,
    String defaultBranch,
    String webUrl,
    String remoteUrl
) {
}
