package com.mappo.controlplane.integrations.azure.templatespec.config;

import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceConfig;

public record TemplateSpecResourceArtifactSourceConfig(
    String descriptor,
    String versionRefField
) implements ProjectReleaseArtifactSourceConfig {

    public static TemplateSpecResourceArtifactSourceConfig defaults() {
        return new TemplateSpecResourceArtifactSourceConfig("template_spec_release", "sourceVersionRef");
    }
}
