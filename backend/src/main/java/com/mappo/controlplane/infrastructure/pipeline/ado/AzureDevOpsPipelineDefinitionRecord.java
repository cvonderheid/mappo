package com.mappo.controlplane.infrastructure.pipeline.ado;

public record AzureDevOpsPipelineDefinitionRecord(
    String id,
    String name,
    String folder,
    String webUrl,
    String apiUrl
) {
}
