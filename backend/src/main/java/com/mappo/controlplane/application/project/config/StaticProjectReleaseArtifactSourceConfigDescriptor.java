package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;

public record StaticProjectReleaseArtifactSourceConfigDescriptor(
    ProjectReleaseArtifactSourceType key,
    Class<? extends ProjectReleaseArtifactSourceConfig> configType,
    ProjectReleaseArtifactSourceConfig defaults
) implements ProjectReleaseArtifactSourceConfigDescriptor {
}
