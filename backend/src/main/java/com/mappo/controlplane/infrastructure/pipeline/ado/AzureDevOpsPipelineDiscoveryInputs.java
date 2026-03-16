package com.mappo.controlplane.infrastructure.pipeline.ado;

public record AzureDevOpsPipelineDiscoveryInputs(
    String organization,
    String project,
    String personalAccessToken
) {
}
