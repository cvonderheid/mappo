package com.mappo.controlplane.integrations.azure.templatespec;

import java.util.Map;

record TemplateSpecDeploymentInputs(
    String tenantId,
    String subscriptionId,
    String resourceGroupName,
    Map<String, Object> template
) {
}
