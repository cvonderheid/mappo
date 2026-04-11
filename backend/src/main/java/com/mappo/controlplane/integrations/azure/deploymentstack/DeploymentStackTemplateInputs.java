package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.resourcemanager.resources.models.DeploymentParameter;
import java.util.Map;

record DeploymentStackTemplateInputs(
    String deploymentScope,
    Object template,
    Map<String, DeploymentParameter> parameters
) {
}
