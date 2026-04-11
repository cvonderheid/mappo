package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.model.RunPreviewMode;

public record StaticProjectDeploymentDriverConfigDescriptor(
    ProjectDeploymentDriverType key,
    Class<? extends ProjectDeploymentDriverConfig> configType,
    ProjectDeploymentDriverConfig defaults,
    ProjectDeploymentDriverCapabilitiesResolver capabilitiesResolver
) implements ProjectDeploymentDriverConfigDescriptor {

    @Override
    public DeploymentDriverCapabilities capabilities(
        ProjectDeploymentDriverConfig config,
        boolean hasDeploymentDriver,
        RunPreviewMode previewMode
    ) {
        return capabilitiesResolver.resolve(config, hasDeploymentDriver, previewMode);
    }
}
