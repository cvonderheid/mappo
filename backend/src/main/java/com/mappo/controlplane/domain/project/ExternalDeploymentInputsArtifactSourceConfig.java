package com.mappo.controlplane.domain.project;

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
