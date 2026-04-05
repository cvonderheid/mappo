package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfigurationPatchRequest(
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
