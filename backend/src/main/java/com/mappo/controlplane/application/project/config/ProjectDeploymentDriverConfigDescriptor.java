package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.model.RunPreviewMode;

public interface ProjectDeploymentDriverConfigDescriptor
    extends ProjectConfigDescriptor<ProjectDeploymentDriverType, ProjectDeploymentDriverConfig> {

    DeploymentDriverCapabilities capabilities(
        ProjectDeploymentDriverConfig config,
        boolean hasDeploymentDriver,
        RunPreviewMode previewMode
    );
}
