package com.mappo.controlplane.service.run;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentStackInner;
import com.azure.resourcemanager.resources.models.ActionOnUnmanage;
import com.azure.resourcemanager.resources.models.DeploymentStacksDeleteDetachEnum;
import com.azure.resourcemanager.resources.models.DenySettings;
import com.azure.resourcemanager.resources.models.DenySettingsMode;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackExecutor implements DeploymentStackExecutor {

    private final AzureExecutorClient azureExecutorClient;
    private final DeploymentStackTemplateInputsFactory templateInputsFactory;
    private final MappoProperties properties;

    @Override
    public TargetDeploymentOutcome deploy(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        String tenantId = uuidText(target.tenantId(), "tenantId");
        String subscriptionId = uuidText(target.subscriptionId(), "subscriptionId");
        String stackName = resolveStackName(target);
        String deploymentScope = normalize(target.managedResourceGroupId());
        String stackResourceGroupName = resourceGroupNameFromResourceId(deploymentScope);
        ResourceManager resourceManager = null;

        try {
            resourceManager = azureExecutorClient.createResourceManager(tenantId, subscriptionId);
            DeploymentStackTemplateInputs inputs = templateInputsFactory.resolve(release, target);
            DeploymentStackInner deploymentStack = buildDeploymentStack(target.targetId(), inputs);
            DeploymentStackInner result = resourceManager.deploymentStackClient()
                .getDeploymentStacks()
                .createOrUpdateAtResourceGroup(stackResourceGroupName, stackName, deploymentStack);

            DeploymentStackInner diagnosticsStack = refreshStackState(resourceManager, stackResourceGroupName, stackName, result);
            String diagnosticsDeploymentName = deploymentNameFromResourceId(diagnosticsStack.deploymentId());
            String correlationId = fallbackCorrelationId(
                firstNonBlank(normalize(diagnosticsStack.correlationId()), normalize(result.correlationId())),
                runId,
                target.targetId()
            );
            ManagementError error = diagnosticsStack.error();
            if (error != null) {
                DeploymentOperationInner failedOperation = loadFailedDeploymentOperation(
                    resourceManager,
                    stackResourceGroupName,
                    diagnosticsDeploymentName
                );
                throw deploymentFailure(
                    "Azure deployment stack completed with an error state.",
                    error,
                    null,
                    null,
                    null,
                    correlationId,
                    firstNonBlank(diagnosticsDeploymentName, stackName),
                    normalize(diagnosticsStack.id()),
                    normalize(diagnosticsStack.deploymentId()),
                    diagnosticsStack.failedResources(),
                    failedOperation
                );
            }

            String provisioningState = normalize(diagnosticsStack.provisioningState());
            if (!"succeeded".equalsIgnoreCase(provisioningState)) {
                DeploymentOperationInner failedOperation = loadFailedDeploymentOperation(
                    resourceManager,
                    stackResourceGroupName,
                    diagnosticsDeploymentName
                );
                AzureFailureDiagnostics.AzureFailureSnapshot snapshot = AzureFailureDiagnostics.summarize(
                    "Azure deployment stack finished with provisioning state " + provisioningState + ".",
                    error,
                    null,
                    null,
                    null,
                    correlationId,
                    firstNonBlank(diagnosticsDeploymentName, stackName),
                    normalize(diagnosticsStack.deploymentId()),
                    normalize(diagnosticsStack.id()),
                    diagnosticsStack.failedResources(),
                    failedOperation
                );
                throw new TargetDeploymentException(
                    snapshot.message(),
                    new StageErrorRecord(
                        "AZURE_DEPLOYMENT_STACK_NOT_SUCCEEDED",
                        snapshot.message(),
                        snapshot.details()
                    ),
                    correlationId,
                    ""
                );
            }

            return new TargetDeploymentOutcome(
                correlationId,
                "Deployment Stack " + stackName + " succeeded.",
                ""
            );
        } catch (ManagementException error) {
            ManagementError managementError = error.getValue();
            if (isNonTerminalStateConflict(managementError) && resourceManager != null) {
                return attachToInFlightDeployment(
                    resourceManager,
                    stackResourceGroupName,
                    stackName,
                    deploymentScope,
                    runId,
                    target.targetId(),
                    managementError,
                    error
                );
            }
            String correlationId = fallbackCorrelationId(
                responseHeader(error, "x-ms-correlation-request-id"),
                runId,
                target.targetId()
            );
            throw deploymentFailure(
                "Azure deployment stack request failed.",
                managementError,
                error.getResponse() == null ? null : error.getResponse().getStatusCode(),
                responseHeader(error, "x-ms-request-id"),
                responseHeader(error, "x-ms-arm-service-request-id"),
                correlationId,
                stackName,
                deploymentScope,
                "",
                null,
                null
            );
        } catch (IllegalArgumentException error) {
            throw new TargetDeploymentException(
                error.getMessage(),
                new StageErrorRecord(
                    "AZURE_DEPLOYMENT_STACK_CONFIGURATION_INVALID",
                    error.getMessage(),
                    new StageErrorDetailsRecord(
                        null,
                        error.getMessage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        stackName,
                        null,
                        deploymentScope
                    )
                ),
                fallbackCorrelationId(null, runId, target.targetId()),
                ""
            );
        }
    }

    private TargetDeploymentOutcome attachToInFlightDeployment(
        ResourceManager resourceManager,
        String resourceGroupName,
        String stackName,
        String deploymentScope,
        String runId,
        String targetId,
        ManagementError originalError,
        ManagementException managementException
    ) {
        DeploymentStackInner diagnosticsStack = waitForTerminalStackState(resourceManager, resourceGroupName, stackName);
        if (diagnosticsStack == null) {
            throw deploymentFailure(
                "Azure deployment stack was already in progress and did not reach a terminal state before the recovery timeout.",
                originalError,
                managementException.getResponse() == null ? null : managementException.getResponse().getStatusCode(),
                responseHeader(managementException, "x-ms-request-id"),
                responseHeader(managementException, "x-ms-arm-service-request-id"),
                fallbackCorrelationId(
                    responseHeader(managementException, "x-ms-correlation-request-id"),
                    runId,
                    targetId
                ),
                stackName,
                deploymentScope,
                "",
                null,
                null
            );
        }

        String diagnosticsDeploymentName = deploymentNameFromResourceId(diagnosticsStack.deploymentId());
        String correlationId = fallbackCorrelationId(
            normalize(diagnosticsStack.correlationId()),
            runId,
            targetId
        );
        ManagementError diagnosticsError = diagnosticsStack.error();
        String provisioningState = normalize(diagnosticsStack.provisioningState());

        if ("succeeded".equalsIgnoreCase(provisioningState) && diagnosticsError == null) {
            return new TargetDeploymentOutcome(
                correlationId,
                "Deployment Stack " + stackName + " succeeded after reattaching to the in-flight Azure operation.",
                ""
            );
        }

        DeploymentOperationInner failedOperation = loadFailedDeploymentOperation(
            resourceManager,
            resourceGroupName,
            diagnosticsDeploymentName
        );
        throw deploymentFailure(
            "Azure deployment stack did not succeed after reattaching to the in-flight Azure operation.",
            diagnosticsError == null ? originalError : diagnosticsError,
            managementException.getResponse() == null ? null : managementException.getResponse().getStatusCode(),
            responseHeader(managementException, "x-ms-request-id"),
            responseHeader(managementException, "x-ms-arm-service-request-id"),
            correlationId,
            firstNonBlank(diagnosticsDeploymentName, stackName),
            normalize(diagnosticsStack.id()),
            normalize(diagnosticsStack.deploymentId()),
            diagnosticsStack.failedResources(),
            failedOperation
        );
    }

    private DeploymentStackInner waitForTerminalStackState(
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

    private boolean isNonTerminalStateConflict(ManagementError error) {
        return error != null && "DeploymentStackInNonTerminalState".equalsIgnoreCase(normalize(error.getCode()));
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for deployment stack state to settle.", interrupted);
        }
    }

    private DeploymentStackInner refreshStackState(
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

    private DeploymentStackInner buildDeploymentStack(String targetId, DeploymentStackTemplateInputs inputs) {
        return new DeploymentStackInner()
            .withDescription("MAPPO deployment stack for target " + normalize(targetId))
            .withDeploymentScope(inputs.deploymentScope())
            .withTemplate(inputs.template())
            .withParameters(inputs.parameters())
            .withDenySettings(defaultDenySettings())
            .withActionOnUnmanage(defaultActionOnUnmanage())
            .withBypassStackOutOfSyncError(Boolean.TRUE);
    }

    private ActionOnUnmanage defaultActionOnUnmanage() {
        return new ActionOnUnmanage()
            .withResources(DeploymentStacksDeleteDetachEnum.DETACH)
            .withResourceGroups(DeploymentStacksDeleteDetachEnum.DETACH);
    }

    private DenySettings defaultDenySettings() {
        return new DenySettings().withMode(DenySettingsMode.NONE);
    }

    private String resourceGroupNameFromResourceId(String resourceId) {
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

    private TargetDeploymentException deploymentFailure(
        String prefix,
        ManagementError error,
        Integer statusCode,
        String requestId,
        String armServiceRequestId,
        String correlationId,
        String stackName,
        String resourceId,
        String operationId,
        List<? extends com.azure.resourcemanager.resources.models.ResourceReferenceExtended> failedResources,
        DeploymentOperationInner failedOperation
    ) {
        AzureFailureDiagnostics.AzureFailureSnapshot snapshot = AzureFailureDiagnostics.summarize(
            prefix,
            error,
            statusCode,
            requestId,
            armServiceRequestId,
            correlationId,
            stackName,
            operationId,
            resourceId,
            failedResources,
            failedOperation
        );

        return new TargetDeploymentException(
            snapshot.message(),
            new StageErrorRecord(
                "AZURE_DEPLOYMENT_STACK_FAILED",
                snapshot.message(),
                snapshot.details()
            ),
            correlationId,
            ""
        );
    }

    private DeploymentOperationInner loadFailedDeploymentOperation(
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

    private String deploymentNameFromResourceId(String deploymentId) {
        String normalized = normalize(deploymentId);
        String marker = "/deployments/";
        int markerIndex = normalized.lastIndexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        return normalized.substring(markerIndex + marker.length());
    }

    private String resolveStackName(TargetExecutionContextRecord target) {
        String configured = normalize(target.deploymentStackName());
        return configured.isBlank() ? buildStackName(target.targetId()) : configured;
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

    private String fallbackCorrelationId(String value, String runId, String targetId) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "corr-" + sanitize(runId + "-" + targetId + "-stack");
    }

    private String responseHeader(ManagementException error, String name) {
        if (error.getResponse() == null || error.getResponse().getHeaders() == null) {
            return "";
        }
        return normalize(error.getResponse().getHeaders().getValue(name));
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

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
