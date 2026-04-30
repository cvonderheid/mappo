package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfigurationPatchRequest(
    String name,
    String themeKey,
    String releaseIngestEndpointId,
    String providerConnectionId,
    ProjectAccessStrategyType accessStrategy,
    ProjectAccessStrategyConfigRequest accessStrategyConfig,
    ProjectDeploymentDriverType deploymentDriver,
    ProjectDeploymentDriverConfigRequest deploymentDriverConfig,
    ProjectReleaseArtifactSourceType releaseArtifactSource,
    ProjectReleaseArtifactSourceConfigRequest releaseArtifactSourceConfig,
    ProjectRuntimeHealthProviderType runtimeHealthProvider,
    ProjectRuntimeHealthProviderConfigRequest runtimeHealthProviderConfig
) {
}
