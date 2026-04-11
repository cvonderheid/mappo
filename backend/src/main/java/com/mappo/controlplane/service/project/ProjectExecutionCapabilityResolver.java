package com.mappo.controlplane.service.project;

import com.mappo.controlplane.application.project.config.ProjectDeploymentDriverConfigRegistry;
import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.model.RunPreviewMode;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.service.run.DeploymentDriverRegistry;
import com.mappo.controlplane.service.run.TargetAccessResolverRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectExecutionCapabilityResolver {

    private final ProjectDefinitionResolver projectDefinitionResolver;
    private final TargetAccessResolverRegistry targetAccessResolverRegistry;
    private final DeploymentDriverRegistry deploymentDriverRegistry;
    private final ProjectDeploymentDriverConfigRegistry deploymentDriverConfigRegistry;
    private final ProjectRuntimeReadinessResolver projectRuntimeReadinessResolver;

    public ProjectExecutionCapabilities resolve(ReleaseRecord release) {
        var project = projectDefinitionResolver.resolve(release);
        boolean runtimeConfigured = projectRuntimeReadinessResolver.isRuntimeConfigured(project);
        var deploymentDriver = deploymentDriverRegistry.findDriver(project, release, runtimeConfigured);
        var previewDriver = deploymentDriverRegistry.findPreviewDriver(project, release, runtimeConfigured);
        return new ProjectExecutionCapabilities(
            project,
            runtimeConfigured,
            resolveDriverCapabilities(project, deploymentDriver.isPresent(), previewDriver.map(driver -> driver.mode()).orElse(RunPreviewMode.UNSUPPORTED)),
            targetAccessResolverRegistry.getResolver(project, release, runtimeConfigured),
            deploymentDriver,
            previewDriver
        );
    }

    private DeploymentDriverCapabilities resolveDriverCapabilities(
        com.mappo.controlplane.domain.project.ProjectDefinition project,
        boolean hasDeploymentDriver,
        RunPreviewMode previewMode
    ) {
        return deploymentDriverConfigRegistry.capabilities(
            project.deploymentDriver(),
            project.deploymentDriverConfig(),
            hasDeploymentDriver,
            previewMode
        );
    }
}
