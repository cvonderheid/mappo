package com.mappo.controlplane.infrastructure.pipeline.ado;

public record AzureDevOpsProjectDefinitionRecord(
    String id,
    String name,
    String webUrl
) {
}
