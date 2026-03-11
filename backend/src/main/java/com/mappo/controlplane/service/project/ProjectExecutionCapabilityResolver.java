package com.mappo.controlplane.service.project;

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
        return new ProjectExecutionCapabilities(
            project,
            targetAccessResolverRegistry.getResolver(project, release, azureConfigured),
            deploymentDriverRegistry.findDriver(project, release, azureConfigured),
            deploymentDriverRegistry.findPreviewDriver(project, release, azureConfigured)
        );
    }
}
