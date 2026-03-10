package com.mappo.controlplane.service.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentStackInner;
import com.azure.resourcemanager.resources.models.DeploymentStackProvisioningState;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AzureDeploymentStackExecutorTests {

    @Test
    void deployAttachesToInFlightStackWhenAzureReturnsNonTerminalConflict() {
        AzureExecutorClient azureExecutorClient = mock(AzureExecutorClient.class);
        DeploymentStackTemplateInputsFactory templateInputsFactory = mock(DeploymentStackTemplateInputsFactory.class);
        MappoProperties properties = new MappoProperties();
        properties.getAzure().setDeploymentStackAttachTimeoutMs(2_000L);
        properties.getAzure().setDeploymentStackAttachPollIntervalMs(10L);
        AzureDeploymentStackRequestFactory requestFactory = new AzureDeploymentStackRequestFactory();
        AzureDeploymentStackStateService stateService = new AzureDeploymentStackStateService(properties);
        AzureDeploymentStackExecutor executor = new AzureDeploymentStackExecutor(
            azureExecutorClient,
            templateInputsFactory,
            requestFactory,
            stateService
        );

        ResourceManager resourceManager = mock(ResourceManager.class, RETURNS_DEEP_STUBS);
        when(azureExecutorClient.createResourceManager(
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002"
        )).thenReturn(resourceManager);
        when(templateInputsFactory.resolve(any(), any())).thenReturn(new DeploymentStackTemplateInputs(
            "/subscriptions/00000000-0000-0000-0000-000000000002/resourceGroups/rg-demo-target",
            Map.of("$schema", "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#"),
            Map.of()
        ));

        ManagementException inFlightException = mock(ManagementException.class);
        var managementError = mock(com.azure.core.management.exception.ManagementError.class);
        when(managementError.getCode()).thenReturn("DeploymentStackInNonTerminalState");
        when(managementError.getMessage()).thenReturn("stack already deploying");
        when(inFlightException.getValue()).thenReturn(managementError);
        when(resourceManager.deploymentStackClient()
            .getDeploymentStacks()
            .createOrUpdateAtResourceGroup(eq("rg-demo-target"), eq("mappo-stack-demo-target"), any()))
            .thenThrow(inFlightException);

        DeploymentStackInner deploying = mock(DeploymentStackInner.class);
        when(deploying.provisioningState()).thenReturn(DeploymentStackProvisioningState.DEPLOYING);
        DeploymentStackInner succeeded = mock(DeploymentStackInner.class);
        when(succeeded.provisioningState()).thenReturn(DeploymentStackProvisioningState.SUCCEEDED);
        when(succeeded.correlationId()).thenReturn("azure-corr-123");
        when(succeeded.id()).thenReturn("/subscriptions/00000000-0000-0000-0000-000000000002/resourceGroups/rg-demo-target/providers/Microsoft.Resources/deploymentStacks/mappo-stack-demo-target");
        when(succeeded.deploymentId()).thenReturn("/subscriptions/00000000-0000-0000-0000-000000000002/resourceGroups/rg-demo-target/providers/Microsoft.Resources/deployments/mappo-stack-demo-target");
        when(succeeded.error()).thenReturn(null);
        when(succeeded.failedResources()).thenReturn(null);
        when(resourceManager.deploymentStackClient()
            .getDeploymentStacks()
            .getByResourceGroup("rg-demo-target", "mappo-stack-demo-target"))
            .thenReturn(deploying, succeeded);

        TargetExecutionContextRecord target = new TargetExecutionContextRecord(
            "demo-target",
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "/subscriptions/00000000-0000-0000-0000-000000000002/resourceGroups/rg-demo-target",
            "/subscriptions/00000000-0000-0000-0000-000000000002/resourceGroups/rg-demo-target/providers/Microsoft.App/containerApps/ca-demo-target",
            null,
            null,
            "",
            "",
            "",
            Map.of(),
            null
        );
        ReleaseRecord release = new ReleaseRecord(
            "rel-demo",
            "github://cvonderheid/mappo-managed-app/managed-app/mainTemplate.json",
            "2026.03.09.1",
            MappoReleaseSourceType.deployment_stack,
            "https://storage.example.com/releases/2026.03.09.1/mainTemplate.json",
            MappoDeploymentScope.resource_group,
            null,
            Map.of(),
            "test release",
            java.util.List.of(),
            OffsetDateTime.now()
        );

        TargetDeploymentOutcome outcome = executor.deploy("run-demo", release, target);

        assertThat(outcome.message()).contains("reattaching to the in-flight Azure operation");
        assertThat(outcome.correlationId()).isEqualTo("azure-corr-123");
    }
}
