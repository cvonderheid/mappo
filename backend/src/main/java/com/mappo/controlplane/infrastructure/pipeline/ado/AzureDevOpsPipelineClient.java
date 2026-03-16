package com.mappo.controlplane.infrastructure.pipeline.ado;

import java.util.List;

public interface AzureDevOpsPipelineClient {

    AzureDevOpsPipelineRunRecord queueRun(AzureDevOpsPipelineInputs inputs);

    AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId);

    List<AzureDevOpsPipelineDefinitionRecord> listPipelines(AzureDevOpsPipelineDiscoveryInputs inputs);
}
