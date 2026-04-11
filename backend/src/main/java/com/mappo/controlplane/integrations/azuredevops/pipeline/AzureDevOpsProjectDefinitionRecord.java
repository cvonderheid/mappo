package com.mappo.controlplane.integrations.azuredevops.pipeline;

public record AzureDevOpsProjectDefinitionRecord(
    String id,
    String name,
    String webUrl
) {
}
