package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.ReleaseMaterializerRegistry;
import com.mappo.controlplane.service.run.DeploymentStackExecutor;
import com.mappo.controlplane.service.run.TargetDeploymentException;
import com.mappo.controlplane.service.run.TargetDeploymentOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackExecutor implements DeploymentStackExecutor {

    private final AzureExecutorClient azureExecutorClient;
    private final ReleaseMaterializerRegistry releaseMaterializerRegistry;
    private final AzureDeploymentStackRequestFactory requestFactory;
    private final AzureDeploymentStackStateService stateService;
    private final AzureDeploymentStackRecoveryService recoveryService;
    private final AzureDeploymentStackFailureFactory failureFactory;

    @Override
    public TargetDeploymentOutcome deploy(
        String runId,
        ProjectDefinition project,
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
            DeploymentStackTemplateInputs inputs = releaseMaterializerRegistry.materialize(
                project,
                release,
                target,
                azureExecutorClient.isConfigured(),
                DeploymentStackTemplateInputs.class
            );
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
                    resourceManager,
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
                ""
            );
        } catch (ManagementException error) {
            ManagementError managementError = error.getValue();
            if (stateService.isNonTerminalStateConflict(managementError) && resourceManager != null) {
                return recoveryService.attachToInFlightDeployment(
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
            throw failureFactory.deploymentFailure(
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

    private String responseHeader(ManagementException error, String name) {
        if (error.getResponse() == null || error.getResponse().getHeaders() == null) {
            return "";
        }
        return requestFactory.normalize(error.getResponse().getHeaders().getValue(name));
    }
}
