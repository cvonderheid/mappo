package com.mappo.controlplane.integrations.azure.deploymentstack.config;

import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceConfig;

public record BlobArmTemplateArtifactSourceConfig(
    String descriptor,
    String templateUriField
) implements ProjectReleaseArtifactSourceConfig {

    public static BlobArmTemplateArtifactSourceConfig defaults() {
        return new BlobArmTemplateArtifactSourceConfig("blob_uri_manifest", "templateUri");
    }
}
