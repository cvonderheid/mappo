package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.azure.resourcemanager.resources.models.DeploymentParameter;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class DeploymentStackParameterFactory {

    private final DeploymentStackRegistryParameterResolver registryParameterResolver;

    DeploymentStackParameterFactory(DeploymentStackRegistryParameterResolver registryParameterResolver) {
        this.registryParameterResolver = registryParameterResolver;
    }

    public Map<String, DeploymentParameter> deploymentParameters(
        Map<String, String> defaults,
        TargetExecutionContextRecord target,
        ContainerApp currentContainerApp
    ) {
        Map<String, DeploymentParameter> parameters = new LinkedHashMap<>();
        mergeParameterValues(parameters, defaults);
        mergeParameterValues(parameters, targetParameterDefaults(defaults, target, currentContainerApp));
        return parameters;
    }

    private void mergeParameterValues(Map<String, DeploymentParameter> targetParameters, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = normalize(entry.getKey());
            if (key.isBlank()) {
                continue;
            }
            targetParameters.put(key, new DeploymentParameter().withValue(entry.getValue()));
        }
    }

    private Map<String, String> targetParameterDefaults(
        Map<String, String> defaults,
        TargetExecutionContextRecord target,
        ContainerApp currentContainerApp
    ) {
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
        values.putAll(registryParameterResolver.registryParameters(defaults, target));
        return values;
    }

    private String uuidText(Object value, String fieldName) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for deployment_stack execution");
        }
        return text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
