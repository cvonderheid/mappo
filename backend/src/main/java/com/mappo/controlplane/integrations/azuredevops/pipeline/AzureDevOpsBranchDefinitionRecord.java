package com.mappo.controlplane.integrations.azuredevops.pipeline;

public record AzureDevOpsBranchDefinitionRecord(
    String name,
    String refName
) {
}
