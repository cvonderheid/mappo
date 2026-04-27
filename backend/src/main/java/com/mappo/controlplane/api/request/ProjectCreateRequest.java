package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectCreateRequest(
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String id,
    @NotBlank String name,
    String themeKey,
    String releaseIngestEndpointId,
    String providerConnectionId,
    @NotNull ProjectAccessStrategyType accessStrategy,
    Map<String, Object> accessStrategyConfig,
    @NotNull ProjectDeploymentDriverType deploymentDriver,
    Map<String, Object> deploymentDriverConfig,
    @NotNull ProjectReleaseArtifactSourceType releaseArtifactSource,
    Map<String, Object> releaseArtifactSourceConfig,
    @NotNull ProjectRuntimeHealthProviderType runtimeHealthProvider,
    Map<String, Object> runtimeHealthProviderConfig
) {
}
