package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.resourcemanager.resources.ResourceManager;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.ReleaseMaterializerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackOperationContextFactory {

    private final AzureExecutorClient azureExecutorClient;
    private final ReleaseMaterializerRegistry releaseMaterializerRegistry;
    private final AzureDeploymentStackSupport support;

    public AzureDeploymentStackOperationContext resolve(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        String tenantId = support.uuidText(target.tenantId(), "tenantId", "deployment_stack execution");
        String subscriptionId = support.uuidText(target.subscriptionId(), "subscriptionId", "deployment_stack execution");
        ResourceManager resourceManager = azureExecutorClient.createResourceManager(tenantId, subscriptionId);
        DeploymentStackTemplateInputs inputs = releaseMaterializerRegistry.materialize(
            project,
            release,
            target,
            azureExecutorClient.isConfigured(),
            DeploymentStackTemplateInputs.class
        );
        String deploymentScope = support.normalize(inputs.deploymentScope());
        return new AzureDeploymentStackOperationContext(
            support.resolveStackName(target),
            deploymentScope,
            support.resourceGroupNameFromResourceId(deploymentScope),
            resourceManager,
            inputs
        );
    }
}
