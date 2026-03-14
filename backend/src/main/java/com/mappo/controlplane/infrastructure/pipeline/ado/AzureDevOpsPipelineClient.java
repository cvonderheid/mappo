package com.mappo.controlplane.infrastructure.pipeline.ado;

public interface AzureDevOpsPipelineClient {

    AzureDevOpsPipelineRunRecord queueRun(AzureDevOpsPipelineInputs inputs);

    AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId);
}
