package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.fluent.models.WhatIfOperationResultInner;
import com.mappo.controlplane.domain.execution.TargetPreviewOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackPreviewOperationService {

    private final AzureDeploymentStackPreviewRequestFactory requestFactory;
    private final AzureDeploymentStackPreviewResultMapper resultMapper;

    public TargetPreviewOutcome preview(AzureDeploymentStackOperationContext context, String targetId) {
        String resourceGroupName = context.resourceGroupName();
        String deploymentName = requestFactory.buildPreviewDeploymentName(targetId);
        try {
            WhatIfOperationResultInner result = context.resourceManager().serviceClient()
                .getDeployments()
                .whatIf(resourceGroupName, deploymentName, requestFactory.whatIfRequest(context.inputs()));

            if (result.error() != null) {
                throw resultMapper.previewFailure(
                    "ARM what-if preview failed.",
                    result.error(),
                    null,
                    null,
                    null,
                    deploymentName,
                    context.deploymentScope()
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
                context.deploymentScope()
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
