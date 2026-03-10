package com.mappo.controlplane.service.run;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.models.Deployment;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureTemplateSpecExecutor implements TemplateSpecExecutor {

    private final AzureExecutorClient azureExecutorClient;
    private final AzureTemplateSpecRequestFactory requestFactory;
    private final AzureTemplateSpecStateService stateService;

    @Override
    public TargetDeploymentOutcome deploy(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        String tenantId = requestFactory.uuidText(target.tenantId(), "tenantId");
        String subscriptionId = requestFactory.uuidText(target.subscriptionId(), "subscriptionId");
        String templateSpecVersionId = requestFactory.resolveTemplateSpecVersionId(release, subscriptionId);
        String resourceGroupName = requestFactory.parseResourceGroupName(target.managedResourceGroupId(), target.targetId());
        String deploymentName = requestFactory.buildDeploymentName(runId, target.targetId());

        try {
            ContainerAppsApiManager containerAppsManager = azureExecutorClient.createContainerAppsManager(tenantId, subscriptionId);
            ContainerApp currentContainerApp = containerAppsManager.containerApps().getById(target.containerAppResourceId());
            ResourceManager resourceManager = azureExecutorClient.createResourceManager(tenantId, subscriptionId);

            Deployment deployment = resourceManager
                .deployments()
                .define(deploymentName)
                .withExistingResourceGroup(resourceGroupName)
                .withTemplate(requestFactory.wrapperTemplate(templateSpecVersionId, release, target, currentContainerApp))
                .withParameters(Map.of())
                .withMode(requestFactory.toAzureMode(release.executionSettings().armMode()))
                .create();

            String correlationId = requestFactory.fallbackCorrelationId(deployment.correlationId(), runId, target.targetId());
            ManagementError error = deployment.error();
            if (error != null) {
                DeploymentOperationInner failedOperation = stateService.loadFailedDeploymentOperation(resourceManager, resourceGroupName, deploymentName);
                throw deploymentFailure(
                    "Azure deployment completed with an error state.",
                    error,
                    null,
                    null,
                    null,
                    correlationId,
                    deploymentName,
                    target.containerAppResourceId(),
                    failedOperation
                );
            }

            String provisioningState = requestFactory.normalize(deployment.provisioningState());
            if (!"succeeded".equalsIgnoreCase(provisioningState)) {
                DeploymentOperationInner failedOperation = stateService.loadFailedDeploymentOperation(resourceManager, resourceGroupName, deploymentName);
                AzureFailureDiagnostics.AzureFailureSnapshot snapshot = AzureFailureDiagnostics.summarize(
                    "Azure deployment finished with provisioning state " + provisioningState + ".",
                    error,
                    null,
                    null,
                    null,
                    correlationId,
                    deploymentName,
                    null,
                    target.containerAppResourceId(),
                    null,
                    failedOperation
                );
                throw new TargetDeploymentException(
                    snapshot.message(),
                    new StageErrorRecord(
                        "AZURE_DEPLOYMENT_NOT_SUCCEEDED",
                        snapshot.message(),
                        snapshot.details()
                    ),
                    correlationId,
                    ""
                );
            }

            return new TargetDeploymentOutcome(
                correlationId,
                "Template Spec deployment " + deploymentName + " succeeded.",
                ""
            );
        } catch (ManagementException error) {
            ManagementError managementError = error.getValue();
            String correlationId = requestFactory.fallbackCorrelationId(
                responseHeader(error, "x-ms-correlation-request-id"),
                runId,
                target.targetId()
            );
            ResourceManager resourceManager = azureExecutorClient.createResourceManager(tenantId, subscriptionId);
            DeploymentOperationInner failedOperation = stateService.loadFailedDeploymentOperation(resourceManager, resourceGroupName, deploymentName);
            throw deploymentFailure(
                "Azure deployment request failed.",
                managementError,
                error.getResponse() == null ? null : error.getResponse().getStatusCode(),
                responseHeader(error, "x-ms-request-id"),
                responseHeader(error, "x-ms-arm-service-request-id"),
                correlationId,
                deploymentName,
                target.containerAppResourceId(),
                failedOperation
            );
        } catch (IllegalArgumentException error) {
            throw new TargetDeploymentException(
                error.getMessage(),
                new StageErrorRecord(
                    "AZURE_DEPLOYMENT_CONFIGURATION_INVALID",
                    error.getMessage(),
                    new StageErrorDetailsRecord(
                        null,
                        error.getMessage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        requestFactory.fallbackCorrelationId(null, runId, target.targetId()),
                        deploymentName,
                        null,
                        target.containerAppResourceId()
                    )
                ),
                requestFactory.fallbackCorrelationId(null, runId, target.targetId()),
                ""
            );
        }
    }

    private TargetDeploymentException deploymentFailure(
        String prefix,
        ManagementError error,
        Integer statusCode,
        String requestId,
        String armServiceRequestId,
        String correlationId,
        String deploymentName,
        String resourceId,
        DeploymentOperationInner failedOperation
    ) {
        AzureFailureDiagnostics.AzureFailureSnapshot snapshot = AzureFailureDiagnostics.summarize(
            prefix,
            error,
            statusCode,
            requestId,
            armServiceRequestId,
            correlationId,
            deploymentName,
            null,
            resourceId,
            null,
            failedOperation
        );

        return new TargetDeploymentException(
            snapshot.message(),
            new StageErrorRecord(
                "AZURE_DEPLOYMENT_FAILED",
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
