package com.mappo.controlplane.infrastructure.pipeline.ado;

import java.util.Map;

public record AzureDevOpsPipelineInputs(
    String organization,
    String project,
    String pipelineId,
    String branch,
    String descriptorPath,
    String versionField,
    String azureServiceConnectionName,
    String targetTenantId,
    String targetSubscriptionId,
    String targetId,
    String releaseId,
    String releaseVersion,
    Map<String, String> templateParameters
) {
}
