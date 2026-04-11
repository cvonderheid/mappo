package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.azure.resourcemanager.resources.models.DeploymentWhatIf;
import com.azure.resourcemanager.resources.models.DeploymentWhatIfProperties;
import com.azure.resourcemanager.resources.models.DeploymentWhatIfSettings;
import com.azure.resourcemanager.resources.models.WhatIfResultFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackPreviewRequestFactory {

    private final AzureDeploymentStackSupport support;

    public DeploymentWhatIf whatIfRequest(DeploymentStackTemplateInputs inputs) {
        return new DeploymentWhatIf()
            .withProperties(
                new DeploymentWhatIfProperties()
                    .withMode(DeploymentMode.INCREMENTAL)
                    .withTemplate(inputs.template())
                    .withParameters(inputs.parameters())
                    .withWhatIfSettings(
                        new DeploymentWhatIfSettings().withResultFormat(WhatIfResultFormat.FULL_RESOURCE_PAYLOADS)
                    )
            );
    }

    public String buildPreviewDeploymentName(String targetId) {
        return "mappo-preview-" + support.sanitize(targetId);
    }

    public String normalize(Object value) {
        return support.normalize(value);
    }

    public String uuidText(Object value, String fieldName) {
        return support.uuidText(value, fieldName, "deployment_stack preview");
    }

    public String resourceGroupNameFromResourceId(String resourceId) {
        return support.resourceGroupNameFromResourceId(resourceId);
    }
}
