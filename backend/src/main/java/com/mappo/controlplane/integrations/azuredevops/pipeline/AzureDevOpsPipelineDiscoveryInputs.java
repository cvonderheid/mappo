package com.mappo.controlplane.integrations.azuredevops.pipeline;

public record AzureDevOpsPipelineDiscoveryInputs(
    String organization,
    String project,
    String personalAccessToken
) {
}
