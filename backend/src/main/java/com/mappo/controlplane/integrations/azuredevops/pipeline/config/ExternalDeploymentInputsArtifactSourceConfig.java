package com.mappo.controlplane.integrations.azuredevops.pipeline.config;

import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceConfig;

public record ExternalDeploymentInputsArtifactSourceConfig(
    String sourceSystem,
    String descriptorPath,
    String versionField
) implements ProjectReleaseArtifactSourceConfig {

    public static ExternalDeploymentInputsArtifactSourceConfig defaults() {
        return new ExternalDeploymentInputsArtifactSourceConfig(
            "azure_devops",
            "pipelineInputs",
            "artifactVersion"
        );
    }
}
