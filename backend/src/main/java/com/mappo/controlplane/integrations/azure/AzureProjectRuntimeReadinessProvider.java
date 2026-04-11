package com.mappo.controlplane.integrations.azure;

import com.mappo.controlplane.application.project.ProjectRuntimeReadinessProvider;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.integrations.azure.auth.AzureExecutorClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureProjectRuntimeReadinessProvider implements ProjectRuntimeReadinessProvider {

    private final AzureExecutorClient azureExecutorClient;

    @Override
    public boolean supports(ProjectDefinition project) {
        if (project == null) {
            return false;
        }
        return switch (project.deploymentDriver()) {
            case azure_deployment_stack, azure_template_spec -> true;
            default -> false;
        };
    }

    @Override
    public boolean isConfigured(ProjectDefinition project) {
        return azureExecutorClient.isConfigured();
    }
}
