package com.mappo.controlplane.infrastructure.pipeline.ado;

public record AzureDevOpsBranchDefinitionRecord(
    String name,
    String refName
) {
}
