package com.mappo.controlplane.service.run;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.mappo.controlplane.azure.AzureExecutorClient;
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
    private final AzureDeploymentStackRequestFactory requestFactory;
    private final AzureDeploymentStackStateService stateService;

    @Override
    public TargetDeploymentOutcome deploy(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        String tenantId = requestFactory.uuidText(target.tenantId(), "tenantId");
        String subscriptionId = requestFactory.uuidText(target.subscriptionId(), "subscriptionId");
        String stackName = requestFactory.resolveStackName(target);
        String deploymentScope = requestFactory.normalize(target.managedResourceGroupId());
        String stackResourceGroupName = requestFactory.resourceGroupNameFromResourceId(deploymentScope);
        ResourceManager resourceManager = null;

        try {
            resourceManager = azureExecutorClient.createResourceManager(tenantId, subscriptionId);
            DeploymentStackTemplateInputs inputs = templateInputsFactory.resolve(release, target);
            var deploymentStack = requestFactory.build(target.targetId(), inputs);
            var result = resourceManager.deploymentStackClient()
                .getDeploymentStacks()
                .createOrUpdateAtResourceGroup(stackResourceGroupName, stackName, deploymentStack);

            var diagnosticsStack = stateService.refreshStackState(resourceManager, stackResourceGroupName, stackName, result);
            String diagnosticsDeploymentName = requestFactory.deploymentNameFromResourceId(diagnosticsStack.deploymentId());
            String correlationId = requestFactory.fallbackCorrelationId(
                requestFactory.firstNonBlank(requestFactory.normalize(diagnosticsStack.correlationId()), requestFactory.normalize(result.correlationId())),
                runId,
                target.targetId()
            );
            ManagementError error = diagnosticsStack.error();
            if (error != null) {
                DeploymentOperationInner failedOperation = stateService.loadFailedDeploymentOperation(
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
                    requestFactory.firstNonBlank(diagnosticsDeploymentName, stackName),
                    requestFactory.normalize(diagnosticsStack.id()),
                    requestFactory.normalize(diagnosticsStack.deploymentId()),
                    diagnosticsStack.failedResources(),
                    failedOperation
                );
            }

            String provisioningState = requestFactory.normalize(diagnosticsStack.provisioningState());
            if (!"succeeded".equalsIgnoreCase(provisioningState)) {
                DeploymentOperationInner failedOperation = stateService.loadFailedDeploymentOperation(
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
                    requestFactory.firstNonBlank(diagnosticsDeploymentName, stackName),
                    requestFactory.normalize(diagnosticsStack.deploymentId()),
                    requestFactory.normalize(diagnosticsStack.id()),
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
            if (stateService.isNonTerminalStateConflict(managementError) && resourceManager != null) {
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
            String correlationId = requestFactory.fallbackCorrelationId(
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
                requestFactory.fallbackCorrelationId(null, runId, target.targetId()),
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
        var diagnosticsStack = stateService.waitForTerminalStackState(resourceManager, resourceGroupName, stackName);
        if (diagnosticsStack == null) {
            throw deploymentFailure(
                "Azure deployment stack was already in progress and did not reach a terminal state before the recovery timeout.",
                originalError,
                managementException.getResponse() == null ? null : managementException.getResponse().getStatusCode(),
                responseHeader(managementException, "x-ms-request-id"),
                responseHeader(managementException, "x-ms-arm-service-request-id"),
                requestFactory.fallbackCorrelationId(
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

        String diagnosticsDeploymentName = requestFactory.deploymentNameFromResourceId(diagnosticsStack.deploymentId());
        String correlationId = requestFactory.fallbackCorrelationId(
            requestFactory.normalize(diagnosticsStack.correlationId()),
            runId,
            targetId
        );
        ManagementError diagnosticsError = diagnosticsStack.error();
        String provisioningState = requestFactory.normalize(diagnosticsStack.provisioningState());

        if ("succeeded".equalsIgnoreCase(provisioningState) && diagnosticsError == null) {
            return new TargetDeploymentOutcome(
                correlationId,
                "Deployment Stack " + stackName + " succeeded after reattaching to the in-flight Azure operation.",
                ""
            );
        }

        DeploymentOperationInner failedOperation = stateService.loadFailedDeploymentOperation(
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
            requestFactory.firstNonBlank(diagnosticsDeploymentName, stackName),
            requestFactory.normalize(diagnosticsStack.id()),
            requestFactory.normalize(diagnosticsStack.deploymentId()),
            diagnosticsStack.failedResources(),
            failedOperation
        );
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

    private String responseHeader(ManagementException error, String name) {
        if (error.getResponse() == null || error.getResponse().getHeaders() == null) {
            return "";
        }
        return requestFactory.normalize(error.getResponse().getHeaders().getValue(name));
    }
}
