package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.WhatIfOperationResultInner;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.ReleaseMaterializerRegistry;
import com.mappo.controlplane.service.run.DeploymentStackPreviewExecutor;
import com.mappo.controlplane.service.run.TargetPreviewException;
import com.mappo.controlplane.service.run.TargetPreviewOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackPreviewExecutor implements DeploymentStackPreviewExecutor {

    private final AzureExecutorClient azureExecutorClient;
    private final ReleaseMaterializerRegistry releaseMaterializerRegistry;
    private final AzureDeploymentStackPreviewRequestFactory requestFactory;
    private final AzureDeploymentStackPreviewResultMapper resultMapper;

    @Override
    public TargetPreviewOutcome preview(ProjectDefinition project, ReleaseRecord release, TargetExecutionContextRecord target) {
        String tenantId = requestFactory.uuidText(target.tenantId(), "tenantId");
        String subscriptionId = requestFactory.uuidText(target.subscriptionId(), "subscriptionId");
        DeploymentStackTemplateInputs inputs = releaseMaterializerRegistry.materialize(
            project,
            release,
            target,
            azureExecutorClient.isConfigured(),
            DeploymentStackTemplateInputs.class
        );
        String resourceGroupName = requestFactory.resourceGroupNameFromResourceId(inputs.deploymentScope());
        String deploymentName = requestFactory.buildPreviewDeploymentName(target.targetId());
        try {
            ResourceManager resourceManager = azureExecutorClient.createResourceManager(tenantId, subscriptionId);
            WhatIfOperationResultInner result = resourceManager.serviceClient()
                .getDeployments()
                .whatIf(resourceGroupName, deploymentName, requestFactory.whatIfRequest(inputs));

            if (result.error() != null) {
                throw resultMapper.previewFailure(
                    "ARM what-if preview failed.",
                    result.error(),
                    null,
                    null,
                    null,
                    deploymentName,
                    inputs.deploymentScope()
                );
            }

            return resultMapper.toPreviewOutcome(result.changes());
        } catch (ManagementException error) {
            throw resultMapper.previewFailure(
                "ARM what-if preview request failed.",
                error.getValue(),
                error.getResponse() == null ? null : error.getResponse().getStatusCode(),
                responseHeader(error, "x-ms-request-id"),
                responseHeader(error, "x-ms-arm-service-request-id"),
                deploymentName,
                inputs.deploymentScope()
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
