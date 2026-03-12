package com.mappo.controlplane.service.project;

import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.domain.project.AzureDeploymentStackDriverConfig;
import com.mappo.controlplane.domain.project.AzureTemplateSpecDriverConfig;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
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

    public ProjectExecutionCapabilities resolve(ReleaseRecord release, boolean azureConfigured) {
        var project = projectDefinitionResolver.resolve(release);
        var deploymentDriver = deploymentDriverRegistry.findDriver(project, release, azureConfigured);
        var previewDriver = deploymentDriverRegistry.findPreviewDriver(project, release, azureConfigured);
        return new ProjectExecutionCapabilities(
            project,
            resolveDriverCapabilities(project, deploymentDriver.isPresent(), previewDriver.map(driver -> driver.mode()).orElse(RunPreviewMode.UNSUPPORTED)),
            targetAccessResolverRegistry.getResolver(project, release, azureConfigured),
            deploymentDriver,
            previewDriver
        );
    }

    private DeploymentDriverCapabilities resolveDriverCapabilities(
        com.mappo.controlplane.domain.project.ProjectDefinition project,
        boolean hasDeploymentDriver,
        RunPreviewMode previewMode
    ) {
        return switch (project.deploymentDriverConfig()) {
            case AzureDeploymentStackDriverConfig config -> new DeploymentDriverCapabilities(
                hasDeploymentDriver && config.supportsPreview(),
                config.supportsPreview() ? previewMode : RunPreviewMode.UNSUPPORTED,
                config.supportsExternalExecutionHandle(),
                false,
                false
            );
            case AzureTemplateSpecDriverConfig config -> new DeploymentDriverCapabilities(
                hasDeploymentDriver && config.supportsPreview(),
                config.supportsPreview() ? previewMode : RunPreviewMode.UNSUPPORTED,
                config.supportsExternalExecutionHandle(),
                false,
                false
            );
            case PipelineTriggerDriverConfig config -> new DeploymentDriverCapabilities(
                false,
                RunPreviewMode.UNSUPPORTED,
                config.supportsExternalExecutionHandle(),
                config.supportsExternalLogs(),
                false
            );
        };
    }
}
