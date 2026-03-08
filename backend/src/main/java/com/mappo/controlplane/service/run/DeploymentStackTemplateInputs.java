package com.mappo.controlplane.service.run;

import com.azure.resourcemanager.resources.models.DeploymentParameter;
import java.util.Map;

record DeploymentStackTemplateInputs(
    String deploymentScope,
    Object template,
    Map<String, DeploymentParameter> parameters
) {
}
