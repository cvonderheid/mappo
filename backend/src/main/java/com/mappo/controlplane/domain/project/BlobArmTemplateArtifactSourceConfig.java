package com.mappo.controlplane.domain.project;

public record BlobArmTemplateArtifactSourceConfig(
    String descriptor,
    String templateUriField
) implements ProjectReleaseArtifactSourceConfig {

    public static BlobArmTemplateArtifactSourceConfig defaults() {
        return new BlobArmTemplateArtifactSourceConfig("blob_uri_manifest", "templateUri");
    }
}
