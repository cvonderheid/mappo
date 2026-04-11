package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.resourcemanager.resources.ResourceManager;

record AzureDeploymentStackOperationContext(
    String stackName,
    String deploymentScope,
    String resourceGroupName,
    ResourceManager resourceManager,
    DeploymentStackTemplateInputs inputs
) {
}
