package com.mappo.controlplane.integrations.azure.templatespec;

import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import org.springframework.stereotype.Component;

@Component
public class AzureTemplateSpecStateService {

    public DeploymentOperationInner loadFailedDeploymentOperation(
        ResourceManager resourceManager,
        String resourceGroupName,
        String deploymentName
    ) {
        if (resourceManager == null || resourceGroupName.isBlank() || deploymentName.isBlank()) {
            return null;
        }
        try {
            DeploymentOperationInner latestFailure = null;
            for (DeploymentOperationInner operation : resourceManager.serviceClient()
                .getDeploymentOperations()
                .listByResourceGroup(resourceGroupName, deploymentName)) {
                if (operation == null || operation.properties() == null) {
                    continue;
                }
                if (operation.properties().statusMessage() != null
                    && operation.properties().statusMessage().error() != null) {
                    latestFailure = operation;
                }
            }
            return latestFailure;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
