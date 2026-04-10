package com.mappo.controlplane.integrations.azuredevops.release;

public record AzureDevOpsReleaseWebhookPayloadRecord(
    String eventType,
    String deliveryId,
    String organization,
    String project,
    String pipelineId,
    String pipelineName,
    String branch,
    String runId,
    String runName,
    String runState,
    String runResult,
    String runWebUrl,
    String runApiUrl
) {
}
