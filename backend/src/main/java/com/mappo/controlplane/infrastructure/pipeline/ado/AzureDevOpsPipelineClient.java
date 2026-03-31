package com.mappo.controlplane.infrastructure.pipeline.ado;

import java.util.List;

public interface AzureDevOpsPipelineClient {

    List<AzureDevOpsProjectDefinitionRecord> listProjects(String organization, String personalAccessToken);

    AzureDevOpsPipelineRunRecord queueRun(AzureDevOpsPipelineInputs inputs);

    AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId);

    List<AzureDevOpsRepositoryDefinitionRecord> listRepositories(AzureDevOpsPipelineDiscoveryInputs inputs);

    List<AzureDevOpsPipelineDefinitionRecord> listPipelines(AzureDevOpsPipelineDiscoveryInputs inputs);

    List<AzureDevOpsServiceConnectionDefinitionRecord> listServiceConnections(
        AzureDevOpsPipelineDiscoveryInputs inputs
    );
}
