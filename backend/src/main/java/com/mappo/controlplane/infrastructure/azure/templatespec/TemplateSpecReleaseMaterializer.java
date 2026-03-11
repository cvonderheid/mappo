package com.mappo.controlplane.infrastructure.azure.templatespec;

import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.domain.execution.ReleaseMaterializer;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TemplateSpecReleaseMaterializer implements ReleaseMaterializer<TemplateSpecDeploymentInputs> {

    private final AzureExecutorClient azureExecutorClient;
    private final AzureTemplateSpecRequestFactory requestFactory;

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured) {
        return project.deploymentDriver() == ProjectDeploymentDriverType.azure_template_spec
            && project.releaseArtifactSource() == ProjectReleaseArtifactSourceType.template_spec_resource;
    }

    @Override
    public Class<TemplateSpecDeploymentInputs> materializedType() {
        return TemplateSpecDeploymentInputs.class;
    }

    @Override
    public TemplateSpecDeploymentInputs materialize(ProjectDefinition project, ReleaseRecord release, TargetExecutionContextRecord target) {
        String tenantId = requestFactory.uuidText(target.tenantId(), "tenantId");
        String subscriptionId = requestFactory.uuidText(target.subscriptionId(), "subscriptionId");
        String templateSpecVersionId = requestFactory.resolveTemplateSpecVersionId(release, subscriptionId);
        String resourceGroupName = requestFactory.parseResourceGroupName(target.managedResourceGroupId(), target.targetId());
        ContainerAppsApiManager containerAppsManager = azureExecutorClient.createContainerAppsManager(tenantId, subscriptionId);
        ContainerApp currentContainerApp = containerAppsManager.containerApps().getById(target.containerAppResourceId());
        return new TemplateSpecDeploymentInputs(
            tenantId,
            subscriptionId,
            resourceGroupName,
            requestFactory.wrapperTemplate(templateSpecVersionId, release, target, currentContainerApp)
        );
    }
}
