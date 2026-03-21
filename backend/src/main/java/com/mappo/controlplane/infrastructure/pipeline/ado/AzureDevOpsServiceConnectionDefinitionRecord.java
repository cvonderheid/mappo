package com.mappo.controlplane.infrastructure.pipeline.ado;

public record AzureDevOpsServiceConnectionDefinitionRecord(
    String id,
    String name,
    String type,
    String webUrl
) {
}
