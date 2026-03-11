package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.core.management.exception.ManagementError;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentStackInner;
import com.mappo.controlplane.config.MappoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackStateService {

    private final MappoProperties properties;

    public DeploymentStackInner waitForTerminalStackState(
        ResourceManager resourceManager,
        String resourceGroupName,
        String stackName
    ) {
        long timeoutMs = Math.max(1_000L, properties.getAzure().getDeploymentStackAttachTimeoutMs());
        long pollIntervalMs = Math.max(250L, properties.getAzure().getDeploymentStackAttachPollIntervalMs());
        long deadline = System.nanoTime() + (timeoutMs * 1_000_000L);

        while (System.nanoTime() < deadline) {
            DeploymentStackInner stack = refreshStackState(resourceManager, resourceGroupName, stackName, null);
            if (stack != null && isTerminalProvisioningState(String.valueOf(stack.provisioningState()))) {
                return stack;
            }
            sleep(pollIntervalMs);
        }
        return refreshStackState(resourceManager, resourceGroupName, stackName, null);
    }

    public DeploymentStackInner refreshStackState(
        ResourceManager resourceManager,
        String resourceGroupName,
        String stackName,
        DeploymentStackInner fallback
    ) {
        if (resourceManager == null || resourceGroupName.isBlank() || stackName.isBlank()) {
            return fallback;
        }
        try {
            DeploymentStackInner refreshed = resourceManager.deploymentStackClient()
                .getDeploymentStacks()
                .getByResourceGroup(resourceGroupName, stackName);
            return refreshed == null ? fallback : refreshed;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public DeploymentOperationInner loadFailedDeploymentOperation(
        ResourceManager resourceManager,
        String resourceGroupName,
        String deploymentName
    ) {
        if (resourceManager == null || deploymentName.isBlank() || resourceGroupName.isBlank()) {
            return null;
        }
        try {
            DeploymentOperationInner latestFailure = null;
            for (DeploymentOperationInner operation : resourceManager.serviceClient()
                .getDeploymentOperations()
                .listByResourceGroup(resourceGroupName, deploymentName)) {
                if (operation == null || operation.properties() == null) {
                    continue;
                }
                if (operation.properties().statusMessage() != null
                    && operation.properties().statusMessage().error() != null) {
                    latestFailure = operation;
                }
            }
            return latestFailure;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public boolean isNonTerminalStateConflict(ManagementError error) {
        return error != null && "DeploymentStackInNonTerminalState".equalsIgnoreCase(normalize(error.getCode()));
    }

    private boolean isTerminalProvisioningState(String provisioningState) {
        String normalized = normalize(provisioningState);
        return !normalized.isBlank()
            && !"deploying".equalsIgnoreCase(normalized)
            && !"running".equalsIgnoreCase(normalized)
            && !"updating".equalsIgnoreCase(normalized)
            && !"accepted".equalsIgnoreCase(normalized)
            && !"creating".equalsIgnoreCase(normalized)
            && !"deleting".equalsIgnoreCase(normalized);
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for deployment stack state to settle.", interrupted);
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
