package com.mappo.controlplane.domain.project;

public record ProjectDefinition(
    String id,
    String name,
    String themeKey,
    String releaseIngestEndpointId,
    String providerConnectionId,
    ProjectAccessStrategyType accessStrategy,
    ProjectAccessStrategyConfig accessStrategyConfig,
    ProjectDeploymentDriverType deploymentDriver,
    ProjectDeploymentDriverConfig deploymentDriverConfig,
    ProjectReleaseArtifactSourceType releaseArtifactSource,
    ProjectReleaseArtifactSourceConfig releaseArtifactSourceConfig,
    ProjectRuntimeHealthProviderType runtimeHealthProvider,
    ProjectRuntimeHealthProviderConfig runtimeHealthProviderConfig
) {
}
