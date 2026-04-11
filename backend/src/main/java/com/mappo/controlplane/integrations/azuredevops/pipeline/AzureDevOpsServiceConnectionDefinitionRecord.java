package com.mappo.controlplane.integrations.azuredevops.pipeline;

public record AzureDevOpsServiceConnectionDefinitionRecord(
    String id,
    String name,
    String type,
    String webUrl
) {
}
