package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.mappo.controlplane.model.TargetExecutionContextRecord;
import org.springframework.stereotype.Component;

@Component
public class AzureDeploymentStackSupport {

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

    public String fallbackCorrelationId(String value, String runId, String targetId) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "corr-" + sanitize(runId + "-" + targetId + "-stack");
    }

    public String uuidText(Object value, String fieldName, String operation) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for " + operation);
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

    public String sanitize(String value) {
        String text = normalize(value).toLowerCase();
        String sanitized = text.replaceAll("[^a-z0-9-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "target" : sanitized;
    }

    private String buildStackName(String targetId) {
        String suffix = sanitize(targetId);
        if (suffix.length() > 48) {
            suffix = suffix.substring(0, 48);
        }
        return "mappo-stack-" + suffix;
    }
}
