package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDefinitionProvider;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AzureDeploymentStackProjectDefinitionProvider implements ProjectDefinitionProvider {

    @Override
    public boolean supports(ReleaseRecord release) {
        return release.sourceType() == MappoReleaseSourceType.deployment_stack;
    }

    @Override
    public ProjectDefinition definition(ReleaseRecord release) {
        return new ProjectDefinition(
            "azure-managed-app-deployment-stack",
            "Azure Managed App Deployment Stack",
            ProjectAccessStrategyType.azure_workload_rbac,
            ProjectDeploymentDriverType.azure_deployment_stack,
            ProjectReleaseArtifactSourceType.blob_arm_template,
            ProjectRuntimeHealthProviderType.azure_container_app_http
        );
    }
}
