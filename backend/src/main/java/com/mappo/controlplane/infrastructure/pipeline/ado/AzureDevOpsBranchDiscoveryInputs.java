package com.mappo.controlplane.infrastructure.pipeline.ado;

public record AzureDevOpsBranchDiscoveryInputs(
    String organization,
    String project,
    String repositoryId,
    String repository,
    String personalAccessToken
) {
}
