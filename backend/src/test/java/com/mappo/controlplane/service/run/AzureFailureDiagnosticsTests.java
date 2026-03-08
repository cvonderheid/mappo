package com.mappo.controlplane.service.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.management.exception.ManagementError;
import com.azure.json.JsonProviders;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.models.ResourceReferenceExtended;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AzureFailureDiagnosticsTests {

    @Test
    void summarizePrefersFailedOperationAndKeepsUsefulAzureContext() throws IOException {
        ManagementError genericDeploymentError = ManagementError.fromJson(JsonProviders.createReader("""
            {
              "code": "DeploymentFailed",
              "message": "At least one resource deployment operation failed. Please list deployment operations for details."
            }
            """));

        ResourceReferenceExtended failedResource = ResourceReferenceExtended.fromJson(JsonProviders.createReader("""
            {
              "id": "/subscriptions/sub/resourceGroups/rg/providers/Microsoft.App/containerApps/ca-one",
              "error": {
                "code": "ContainerAppOperationError",
                "message": "Replica failed to become ready."
              }
            }
            """));

        DeploymentOperationInner failedOperation = DeploymentOperationInner.fromJson(JsonProviders.createReader("""
            {
              "id": "/subscriptions/sub/resourceGroups/rg/providers/Microsoft.Resources/deployments/deploy-one/operations/op-one",
              "operationId": "op-one",
              "properties": {
                "serviceRequestId": "req-123",
                "targetResource": {
                  "id": "/subscriptions/sub/resourceGroups/rg/providers/Microsoft.App/containerApps/ca-one"
                },
                "statusMessage": {
                  "error": {
                    "code": "ContainerAppOperationError",
                    "message": "Revision provisioning failed because the image could not be pulled."
                  }
                }
              }
            }
            """));

        AzureFailureDiagnostics.AzureFailureSnapshot snapshot = AzureFailureDiagnostics.summarize(
            "Azure deployment completed with an error state.",
            genericDeploymentError,
            400,
            "request-root",
            "arm-request-root",
            "corr-123",
            "deploy-one",
            "op-root",
            "/subscriptions/sub/resourceGroups/rg/providers/Microsoft.App/containerApps/ca-one",
            List.of(failedResource),
            failedOperation
        );

        assertThat(snapshot.message()).contains("image could not be pulled");
        assertThat(snapshot.details().azureErrorCode()).isEqualTo("ContainerAppOperationError");
        assertThat(snapshot.details().azureErrorMessage()).contains("image could not be pulled");
        assertThat(snapshot.details().azureRequestId()).isEqualTo("request-root");
        assertThat(snapshot.details().azureArmServiceRequestId()).isEqualTo("arm-request-root");
        assertThat(snapshot.details().azureCorrelationId()).isEqualTo("corr-123");
        assertThat(snapshot.details().azureDeploymentName()).isEqualTo("deploy-one");
        assertThat(snapshot.details().azureOperationId()).isEqualTo("op-root");
        assertThat(snapshot.details().azureResourceId())
            .isEqualTo("/subscriptions/sub/resourceGroups/rg/providers/Microsoft.App/containerApps/ca-one");
        assertThat(snapshot.details().error()).contains("failed operation:");
        assertThat(snapshot.details().error()).contains("failed resource:");
    }
}
