package com.mappo.controlplane.integrations.azure.templatespec;

import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AzureTemplateSpecRequestFactory {

    private static final String DEPLOYMENT_TEMPLATE_SCHEMA =
        "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#";
    private static final String DEPLOYMENT_RESOURCE_API_VERSION = "2022-09-01";

    public Map<String, Object> wrapperTemplate(
        String templateSpecVersionId,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        ContainerApp currentContainerApp
    ) {
        Map<String, Object> resourceProperties = new LinkedHashMap<>();
        resourceProperties.put("mode", toAzureMode(release.executionSettings().armMode()).toString());
        resourceProperties.put("templateLink", Map.of("id", templateSpecVersionId));
        resourceProperties.put("parameters", deploymentParameters(release.parameterDefaults(), target, currentContainerApp));

        Map<String, Object> nestedDeployment = new LinkedHashMap<>();
        nestedDeployment.put("type", "Microsoft.Resources/deployments");
        nestedDeployment.put("apiVersion", DEPLOYMENT_RESOURCE_API_VERSION);
        nestedDeployment.put("name", "mappo-linked-template-spec");
        nestedDeployment.put("properties", resourceProperties);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("$schema", DEPLOYMENT_TEMPLATE_SCHEMA);
        template.put("contentVersion", "1.0.0.0");
        template.put("resources", new Object[]{nestedDeployment});
        return template;
    }

    public String resolveTemplateSpecVersionId(ReleaseRecord release, String targetSubscriptionId) {
        String versionRef = normalize(release.sourceVersionRef());
        if (!versionRef.isBlank()) {
            return mirrorTemplateSpecVersionId(versionRef, targetSubscriptionId);
        }

        String sourceRef = normalize(release.sourceRef());
        if (sourceRef.contains("/versions/")) {
            return mirrorTemplateSpecVersionId(sourceRef, targetSubscriptionId);
        }
        if (sourceRef.isBlank()) {
            throw new IllegalArgumentException("release sourceRef is required for template_spec execution");
        }

        String version = normalize(release.sourceVersion());
        if (version.isBlank()) {
            throw new IllegalArgumentException("release sourceVersion is required to resolve the Template Spec version ID");
        }
        return mirrorTemplateSpecVersionId(sourceRef + "/versions/" + version, targetSubscriptionId);
    }

    public String parseResourceGroupName(String managedResourceGroupId, String targetId) {
        String resourceId = normalize(managedResourceGroupId);
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("target " + targetId + " is missing managedResourceGroupId");
        }
        String resourceGroupName = ResourceId.fromString(resourceId).resourceGroupName();
        if (normalize(resourceGroupName).isBlank()) {
            throw new IllegalArgumentException("target " + targetId + " has an invalid managedResourceGroupId");
        }
        return resourceGroupName;
    }

    public String buildDeploymentName(String runId, String targetId) {
        String suffix = sanitize(runId + "-" + targetId);
        if (suffix.length() > 54) {
            suffix = suffix.substring(0, 54);
        }
        return "mappo-" + suffix;
    }

    public String fallbackCorrelationId(String value, String runId, String targetId) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "corr-" + sanitize(runId + "-" + targetId);
    }

    public String uuidText(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.toString();
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

    public DeploymentMode toAzureMode(MappoArmDeploymentMode mode) {
        if (mode == MappoArmDeploymentMode.complete) {
            return DeploymentMode.COMPLETE;
        }
        return DeploymentMode.INCREMENTAL;
    }

    private Map<String, Object> deploymentParameters(
        Map<String, String> defaults,
        TargetExecutionContextRecord target,
        ContainerApp currentContainerApp
    ) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        mergeParameterValues(parameters, defaults);
        mergeParameterValues(parameters, targetParameterDefaults(target, currentContainerApp));
        return parameters;
    }

    private void mergeParameterValues(Map<String, Object> targetParameters, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = normalize(entry.getKey());
            if (key.isBlank()) {
                continue;
            }
            targetParameters.put(key, Map.of("value", entry.getValue()));
        }
    }

    private Map<String, String> targetParameterDefaults(TargetExecutionContextRecord target, ContainerApp currentContainerApp) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("containerAppName", normalize(currentContainerApp.name()));
        values.put(
            "managedEnvironmentResourceId",
            firstNonBlank(currentContainerApp.managedEnvironmentId(), currentContainerApp.environmentId())
        );
        values.put("location", normalize(currentContainerApp.regionName()));
        values.put("targetGroup", firstNonBlank(target.tags().get("ring"), "prod"));
        values.put("tenantId", uuidText(target.tenantId(), "tenantId"));
        values.put("targetId", normalize(target.targetId()));
        values.put("tier", firstNonBlank(target.tags().get("tier"), "standard"));
        values.put("environment", firstNonBlank(target.tags().get("environment"), "prod"));
        return values;
    }

    private String mirrorTemplateSpecVersionId(String templateSpecVersionId, String targetSubscriptionId) {
        String resourceId = normalize(templateSpecVersionId);
        ResourceId parsed = ResourceId.fromString(resourceId);
        String sourceSubscriptionId = normalize(parsed.subscriptionId());
        if (sourceSubscriptionId.isBlank() || sourceSubscriptionId.equalsIgnoreCase(normalize(targetSubscriptionId))) {
            return resourceId;
        }
        return resourceId.replaceFirst(
            "/subscriptions/" + sourceSubscriptionId,
            "/subscriptions/" + normalize(targetSubscriptionId)
        );
    }

    private String sanitize(String value) {
        String text = normalize(value).toLowerCase();
        String sanitized = text.replaceAll("[^a-z0-9-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "deployment" : sanitized;
    }
}
