package com.mappo.controlplane.integrations.azure.deploymentstack;

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
import com.mappo.controlplane.integrations.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.integrations.azure.access.AzureWorkloadRbacTargetAccessContext;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.integrations.azure.runtime.config.AzureContainerAppHttpRuntimeHealthProviderConfig;
import com.mappo.controlplane.integrations.azure.deploymentstack.config.AzureDeploymentStackDriverConfig;
import com.mappo.controlplane.integrations.azure.access.config.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.integrations.azure.deploymentstack.config.BlobArmTemplateArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.ReleaseMaterializerRegistry;
import com.mappo.controlplane.domain.execution.TargetDeploymentOutcome;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AzureDeploymentStackExecutorTests {

    @Test
    void deployAttachesToInFlightStackWhenAzureReturnsNonTerminalConflict() {
        AzureExecutorClient azureExecutorClient = mock(AzureExecutorClient.class);
        ReleaseMaterializerRegistry releaseMaterializerRegistry = mock(ReleaseMaterializerRegistry.class);
        MappoProperties properties = new MappoProperties();
        properties.getAzure().setDeploymentStackAttachTimeoutMs(2_000L);
        properties.getAzure().setDeploymentStackAttachPollIntervalMs(10L);
        AzureDeploymentStackSupport support = new AzureDeploymentStackSupport();
        AzureDeploymentStackOperationContextFactory contextFactory = new AzureDeploymentStackOperationContextFactory(
            azureExecutorClient,
            releaseMaterializerRegistry,
            support
        );
        AzureDeploymentStackRequestFactory requestFactory = new AzureDeploymentStackRequestFactory(support);
        AzureDeploymentStackStateService stateService = new AzureDeploymentStackStateService(properties);
        AzureDeploymentStackFailureFactory failureFactory = new AzureDeploymentStackFailureFactory();
        AzureDeploymentStackRecoveryService recoveryService = new AzureDeploymentStackRecoveryService(
            stateService,
            requestFactory,
            failureFactory
        );
        AzureDeploymentStackApplyService applyService = new AzureDeploymentStackApplyService(
            requestFactory,
            stateService,
            failureFactory
        );
        AzureDeploymentStackExceptionTranslator exceptionTranslator = new AzureDeploymentStackExceptionTranslator(
            stateService,
            requestFactory,
            recoveryService,
            failureFactory
        );
        AzureDeploymentStackExecutor executor = new AzureDeploymentStackExecutor(
            contextFactory,
            applyService,
            exceptionTranslator
        );

        ResourceManager resourceManager = mock(ResourceManager.class, RETURNS_DEEP_STUBS);
        when(azureExecutorClient.createResourceManager(
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002"
        )).thenReturn(resourceManager);
        when(azureExecutorClient.isConfigured()).thenReturn(true);
        when(releaseMaterializerRegistry.materialize(any(), any(), any(), eq(true), eq(DeploymentStackTemplateInputs.class))).thenReturn(new DeploymentStackTemplateInputs(
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
            BuiltinProjects.AZURE_MANAGED_APP_DEPLOYMENT_STACK,
            "github://example-org/mappo-release-catalog/managed-app/mainTemplate.json",
            "2026.03.09.1",
            MappoReleaseSourceType.deployment_stack,
            "https://storage.example.com/releases/2026.03.09.1/mainTemplate.json",
            MappoDeploymentScope.resource_group,
            null,
            Map.of(),
            Map.of(),
            "test release",
            java.util.List.of(),
            OffsetDateTime.now()
        );
        ProjectDefinition project = new ProjectDefinition(
            "azure-managed-app-deployment-stack",
            "Azure Managed App Deployment Stack",
            "harbor-teal",
            null,
            null,
            ProjectAccessStrategyType.azure_workload_rbac,
            AzureWorkloadRbacAccessStrategyConfig.defaults(),
            ProjectDeploymentDriverType.azure_deployment_stack,
            AzureDeploymentStackDriverConfig.defaults(),
            ProjectReleaseArtifactSourceType.blob_arm_template,
            BlobArmTemplateArtifactSourceConfig.defaults(),
            ProjectRuntimeHealthProviderType.azure_container_app_http,
            AzureContainerAppHttpRuntimeHealthProviderConfig.defaults()
        );

        TargetDeploymentOutcome outcome = executor.deploy(
            "run-demo",
            project,
            release,
            target,
            new AzureWorkloadRbacTargetAccessContext(
                "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000002",
                "provider_service_principal"
            )
        );

        assertThat(outcome.message()).contains("reattaching to the in-flight Azure operation");
        assertThat(outcome.correlationId()).isEqualTo("azure-corr-123");
    }
}
