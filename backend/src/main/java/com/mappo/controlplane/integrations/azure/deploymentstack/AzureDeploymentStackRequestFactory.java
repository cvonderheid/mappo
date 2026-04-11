package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.resourcemanager.resources.fluent.models.DeploymentStackInner;
import com.azure.resourcemanager.resources.models.ActionOnUnmanage;
import com.azure.resourcemanager.resources.models.DeploymentStacksDeleteDetachEnum;
import com.azure.resourcemanager.resources.models.DenySettings;
import com.azure.resourcemanager.resources.models.DenySettingsMode;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackRequestFactory {

    private final AzureDeploymentStackSupport support;

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
        return support.resolveStackName(target);
    }

    public String resourceGroupNameFromResourceId(String resourceId) {
        return support.resourceGroupNameFromResourceId(resourceId);
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
        return support.fallbackCorrelationId(value, runId, targetId);
    }

    public String uuidText(Object value, String fieldName) {
        return support.uuidText(value, fieldName, "deployment_stack execution");
    }

    public String firstNonBlank(String... values) {
        return support.firstNonBlank(values);
    }

    public String normalize(Object value) {
        return support.normalize(value);
    }

    private ActionOnUnmanage defaultActionOnUnmanage() {
        return new ActionOnUnmanage()
            .withResources(DeploymentStacksDeleteDetachEnum.DETACH)
            .withResourceGroups(DeploymentStacksDeleteDetachEnum.DETACH);
    }

    private DenySettings defaultDenySettings() {
        return new DenySettings().withMode(DenySettingsMode.NONE);
    }
}
