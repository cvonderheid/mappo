package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;
import com.mappo.controlplane.model.RunPreviewMode;

@FunctionalInterface
public interface ProjectDeploymentDriverCapabilitiesResolver {

    DeploymentDriverCapabilities resolve(
        ProjectDeploymentDriverConfig config,
        boolean hasDeploymentDriver,
        RunPreviewMode previewMode
    );
}
