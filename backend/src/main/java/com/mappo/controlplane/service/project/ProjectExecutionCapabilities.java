package com.mappo.controlplane.service.project;

import com.mappo.controlplane.domain.access.TargetAccessResolver;
import com.mappo.controlplane.domain.execution.DeploymentDriver;
import com.mappo.controlplane.domain.execution.DeploymentPreviewDriver;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import java.util.Optional;

public record ProjectExecutionCapabilities(
    ProjectDefinition project,
    TargetAccessResolver targetAccessResolver,
    Optional<DeploymentDriver> deploymentDriver,
    Optional<DeploymentPreviewDriver> previewDriver
) {
}
