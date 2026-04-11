package com.mappo.controlplane.integrations.azuredevops.pipeline;

public record AzureDevOpsBranchDiscoveryInputs(
    String organization,
    String project,
    String repositoryId,
    String repository,
    String personalAccessToken
) {
}
