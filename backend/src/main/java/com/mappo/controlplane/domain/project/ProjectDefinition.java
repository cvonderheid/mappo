package com.mappo.controlplane.domain.project;

public record ProjectDefinition(
    String id,
    String name,
    ProjectAccessStrategyType accessStrategy,
    ProjectDeploymentDriverType deploymentDriver,
    ProjectReleaseArtifactSourceType releaseArtifactSource,
    ProjectRuntimeHealthProviderType runtimeHealthProvider
) {
}
