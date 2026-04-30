package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectCreateRequest(
    @NotBlank String name,
    String themeKey,
    String releaseIngestEndpointId,
    String providerConnectionId,
    @NotNull ProjectAccessStrategyType accessStrategy,
    ProjectAccessStrategyConfigRequest accessStrategyConfig,
    @NotNull ProjectDeploymentDriverType deploymentDriver,
    ProjectDeploymentDriverConfigRequest deploymentDriverConfig,
    @NotNull ProjectReleaseArtifactSourceType releaseArtifactSource,
    ProjectReleaseArtifactSourceConfigRequest releaseArtifactSourceConfig,
    @NotNull ProjectRuntimeHealthProviderType runtimeHealthProvider,
    ProjectRuntimeHealthProviderConfigRequest runtimeHealthProviderConfig
) {
}
