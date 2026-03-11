package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentStackInner;
import com.mappo.controlplane.service.run.TargetDeploymentOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackRecoveryService {

    private final AzureDeploymentStackStateService stateService;
    private final AzureDeploymentStackRequestFactory requestFactory;
    private final AzureDeploymentStackFailureFactory failureFactory;

    public TargetDeploymentOutcome attachToInFlightDeployment(
        ResourceManager resourceManager,
        String resourceGroupName,
        String stackName,
        String deploymentScope,
        String runId,
        String targetId,
        ManagementError originalError,
        ManagementException managementException
    ) {
        DeploymentStackInner diagnosticsStack = stateService.waitForTerminalStackState(
            resourceManager,
            resourceGroupName,
            stackName
        );
        if (diagnosticsStack == null) {
            throw failureFactory.deploymentFailure(
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
        throw failureFactory.deploymentFailure(
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

    private String responseHeader(ManagementException error, String name) {
        if (error.getResponse() == null || error.getResponse().getHeaders() == null) {
            return "";
        }
        return requestFactory.normalize(error.getResponse().getHeaders().getValue(name));
    }
}
