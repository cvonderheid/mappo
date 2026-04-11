package com.mappo.controlplane.integrations.azuredevops.pipeline;

public record AzureDevOpsPipelineDefinitionRecord(
    String id,
    String name,
    String folder,
    String webUrl,
    String apiUrl
) {
}
