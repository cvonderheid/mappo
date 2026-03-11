package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.service.run.TargetDeploymentException;
import com.mappo.controlplane.service.run.TargetDeploymentOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackExceptionTranslator {

    private final AzureDeploymentStackStateService stateService;
    private final AzureDeploymentStackRequestFactory requestFactory;
    private final AzureDeploymentStackRecoveryService recoveryService;
    private final AzureDeploymentStackFailureFactory failureFactory;

    public TargetDeploymentOutcome handleManagementException(
        AzureDeploymentStackOperationContext context,
        String runId,
        String targetId,
        ManagementException error
    ) {
        ManagementError managementError = error.getValue();
        if (stateService.isNonTerminalStateConflict(managementError)) {
            return recoveryService.attachToInFlightDeployment(
                context.resourceManager(),
                context.resourceGroupName(),
                context.stackName(),
                context.deploymentScope(),
                runId,
                targetId,
                managementError,
                error
            );
        }
        String correlationId = requestFactory.fallbackCorrelationId(
            responseHeader(error, "x-ms-correlation-request-id"),
            runId,
            targetId
        );
        throw failureFactory.deploymentFailure(
            "Azure deployment stack request failed.",
            managementError,
            error.getResponse() == null ? null : error.getResponse().getStatusCode(),
            responseHeader(error, "x-ms-request-id"),
            responseHeader(error, "x-ms-arm-service-request-id"),
            correlationId,
            context.stackName(),
            context.deploymentScope(),
            "",
            null,
            null
        );
    }

    public TargetDeploymentException configurationFailure(
        AzureDeploymentStackOperationContext context,
        String runId,
        String targetId,
        IllegalArgumentException error
    ) {
        return new TargetDeploymentException(
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
                    context == null ? "" : context.stackName(),
                    null,
                    context == null ? "" : context.deploymentScope()
                )
            ),
            requestFactory.fallbackCorrelationId(null, runId, targetId),
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
