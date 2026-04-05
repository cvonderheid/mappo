package com.mappo.controlplane.service.project;

import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import java.util.Map;

public record ProjectConfigurationMutationRecord(
    String id,
    String name,
    String themeKey,
    String releaseIngestEndpointId,
    String providerConnectionId,
    ProjectAccessStrategyType accessStrategy,
    Map<String, Object> accessStrategyConfig,
    ProjectDeploymentDriverType deploymentDriver,
    Map<String, Object> deploymentDriverConfig,
    ProjectReleaseArtifactSourceType releaseArtifactSource,
    Map<String, Object> releaseArtifactSourceConfig,
    ProjectRuntimeHealthProviderType runtimeHealthProvider,
    Map<String, Object> runtimeHealthProviderConfig
) {
}
