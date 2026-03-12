package com.mappo.controlplane.domain.project;

public sealed interface ProjectReleaseArtifactSourceConfig
    permits BlobArmTemplateArtifactSourceConfig, TemplateSpecResourceArtifactSourceConfig, ExternalDeploymentInputsArtifactSourceConfig {
}
