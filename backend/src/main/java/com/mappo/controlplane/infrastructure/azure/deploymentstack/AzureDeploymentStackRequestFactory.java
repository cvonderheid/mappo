package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.resourcemanager.resources.fluent.models.DeploymentStackInner;
import com.azure.resourcemanager.resources.models.ActionOnUnmanage;
import com.azure.resourcemanager.resources.models.DeploymentStacksDeleteDetachEnum;
import com.azure.resourcemanager.resources.models.DenySettings;
import com.azure.resourcemanager.resources.models.DenySettingsMode;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import org.springframework.stereotype.Component;

@Component
public class AzureDeploymentStackRequestFactory {

    public DeploymentStackInner build(String targetId, DeploymentStackTemplateInputs inputs) {
        return new DeploymentStackInner()
            .withDescription("MAPPO deployment stack for target " + normalize(targetId))
            .withDeploymentScope(inputs.deploymentScope())
            .withTemplate(inputs.template())
            .withParameters(inputs.parameters())
            .withDenySettings(defaultDenySettings())
            .withActionOnUnmanage(defaultActionOnUnmanage())
            .withBypassStackOutOfSyncError(Boolean.TRUE);
    }

    public String resolveStackName(TargetExecutionContextRecord target) {
        String configured = normalize(target.deploymentStackName());
        return configured.isBlank() ? buildStackName(target.targetId()) : configured;
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

    public String deploymentNameFromResourceId(String deploymentId) {
        String normalized = normalize(deploymentId);
        String marker = "/deployments/";
        int markerIndex = normalized.lastIndexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        return normalized.substring(markerIndex + marker.length());
    }

    public String fallbackCorrelationId(String value, String runId, String targetId) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "corr-" + sanitize(runId + "-" + targetId + "-stack");
    }

    public String uuidText(Object value, String fieldName) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for deployment_stack execution");
        }
        return text;
    }

    public String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    public String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private ActionOnUnmanage defaultActionOnUnmanage() {
        return new ActionOnUnmanage()
            .withResources(DeploymentStacksDeleteDetachEnum.DETACH)
            .withResourceGroups(DeploymentStacksDeleteDetachEnum.DETACH);
    }

    private DenySettings defaultDenySettings() {
        return new DenySettings().withMode(DenySettingsMode.NONE);
    }

    private String buildStackName(String targetId) {
        String suffix = sanitize(targetId);
        if (suffix.length() > 48) {
            suffix = suffix.substring(0, 48);
        }
        return "mappo-stack-" + suffix;
    }

    private String sanitize(String value) {
        String text = normalize(value).toLowerCase();
        String sanitized = text.replaceAll("[^a-z0-9-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "target" : sanitized;
    }
}
