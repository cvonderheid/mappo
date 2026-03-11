package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DeploymentStackCurrentContainerAppLoader {

    private final AzureExecutorClient azureExecutorClient;

    public ContainerApp load(TargetExecutionContextRecord target) {
        String tenantId = uuidText(target.tenantId(), "tenantId");
        String subscriptionId = uuidText(target.subscriptionId(), "subscriptionId");
        ContainerAppsApiManager containerAppsManager = azureExecutorClient.createContainerAppsManager(tenantId, subscriptionId);
        return containerAppsManager.containerApps().getById(target.containerAppResourceId());
    }

    private String uuidText(Object value, String fieldName) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for deployment_stack execution");
        }
        return text;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
