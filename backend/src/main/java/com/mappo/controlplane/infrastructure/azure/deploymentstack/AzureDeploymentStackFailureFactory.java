package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.core.management.exception.ManagementError;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.models.ResourceReferenceExtended;
import com.mappo.controlplane.infrastructure.azure.AzureFailureDiagnostics;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.service.run.TargetDeploymentException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AzureDeploymentStackFailureFactory {

    public TargetDeploymentException deploymentFailure(
        String prefix,
        ManagementError error,
        Integer statusCode,
        String requestId,
        String armServiceRequestId,
        String correlationId,
        String stackName,
        String resourceId,
        String operationId,
        List<? extends ResourceReferenceExtended> failedResources,
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
}
