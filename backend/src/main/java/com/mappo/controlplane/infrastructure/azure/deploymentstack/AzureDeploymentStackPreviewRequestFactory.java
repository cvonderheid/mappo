package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.azure.resourcemanager.resources.models.DeploymentWhatIf;
import com.azure.resourcemanager.resources.models.DeploymentWhatIfProperties;
import com.azure.resourcemanager.resources.models.DeploymentWhatIfSettings;
import com.azure.resourcemanager.resources.models.WhatIfResultFormat;
import org.springframework.stereotype.Component;

@Component
public class AzureDeploymentStackPreviewRequestFactory {

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
        return "mappo-preview-" + sanitize(targetId);
    }

    public String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public String uuidText(Object value, String fieldName) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for deployment_stack preview");
        }
        return text;
    }

    public String resourceGroupNameFromResourceId(String resourceId) {
        String normalized = normalize(resourceId);
        String marker = "/resourceGroups/";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("managedResourceGroupId must be a valid Azure resource group resource ID");
        }
        String remaining = normalized.substring(markerIndex + marker.length());
        int nextSlash = remaining.indexOf('/');
        return nextSlash < 0 ? remaining : remaining.substring(0, nextSlash);
    }

    private String sanitize(String value) {
        String text = normalize(value).toLowerCase();
        String sanitized = text.replaceAll("[^a-z0-9-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "target" : sanitized;
    }
}
