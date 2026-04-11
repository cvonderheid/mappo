package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.core.management.exception.ManagementError;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.mappo.controlplane.model.ExternalExecutionHandleRecord;
import com.mappo.controlplane.domain.execution.TargetDeploymentOutcome;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackApplyService {

    private final AzureDeploymentStackRequestFactory requestFactory;
    private final AzureDeploymentStackStateService stateService;
    private final AzureDeploymentStackFailureFactory failureFactory;

    public TargetDeploymentOutcome apply(
        AzureDeploymentStackOperationContext context,
        String runId,
        String targetId
    ) {
        String stackName = context.stackName();
        String deploymentScope = context.deploymentScope();
        String stackResourceGroupName = context.resourceGroupName();
        var deploymentStack = requestFactory.build(targetId, context.inputs());
        var result = context.resourceManager().deploymentStackClient()
            .getDeploymentStacks()
            .createOrUpdateAtResourceGroup(stackResourceGroupName, stackName, deploymentStack);

        var diagnosticsStack = stateService.refreshStackState(context.resourceManager(), stackResourceGroupName, stackName, result);
        String diagnosticsDeploymentName = requestFactory.deploymentNameFromResourceId(diagnosticsStack.deploymentId());
        String correlationId = requestFactory.fallbackCorrelationId(
            requestFactory.firstNonBlank(requestFactory.normalize(diagnosticsStack.correlationId()), requestFactory.normalize(result.correlationId())),
            runId,
            targetId
        );
        ManagementError error = diagnosticsStack.error();
        if (error != null) {
            DeploymentOperationInner failedOperation = stateService.loadFailedDeploymentOperation(
                context.resourceManager(),
                stackResourceGroupName,
                diagnosticsDeploymentName
            );
            throw failureFactory.deploymentFailure(
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
                context.resourceManager(),
                stackResourceGroupName,
                diagnosticsDeploymentName
            );
            throw failureFactory.deploymentFailure(
                "Azure deployment stack finished with provisioning state " + provisioningState + ".",
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

        return new TargetDeploymentOutcome(
            correlationId,
            "Deployment Stack " + stackName + " succeeded.",
            "",
            new ExternalExecutionHandleRecord(
                "azure_deployment_stack",
                requestFactory.firstNonBlank(requestFactory.normalize(diagnosticsStack.id()), requestFactory.normalize(diagnosticsStack.deploymentId())),
                stackName,
                requestFactory.firstNonBlank(provisioningState, "succeeded"),
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
            )
        );
    }
}
