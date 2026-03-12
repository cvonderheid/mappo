package com.mappo.controlplane.domain.project;

public record TemplateSpecResourceArtifactSourceConfig(
    String descriptor,
    String versionRefField
) implements ProjectReleaseArtifactSourceConfig {

    public static TemplateSpecResourceArtifactSourceConfig defaults() {
        return new TemplateSpecResourceArtifactSourceConfig("template_spec_release", "sourceVersionRef");
    }
}
