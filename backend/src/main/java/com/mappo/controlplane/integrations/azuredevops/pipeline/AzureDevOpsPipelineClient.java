package com.mappo.controlplane.integrations.azuredevops.pipeline;

import java.util.List;

public interface AzureDevOpsPipelineClient {

    List<AzureDevOpsProjectDefinitionRecord> listProjects(String organization, String personalAccessToken);

    AzureDevOpsPipelineRunRecord queueRun(AzureDevOpsPipelineInputs inputs);

    AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId);

    List<AzureDevOpsBranchDefinitionRecord> listBranches(AzureDevOpsBranchDiscoveryInputs inputs);

    List<AzureDevOpsRepositoryDefinitionRecord> listRepositories(AzureDevOpsPipelineDiscoveryInputs inputs);

    List<AzureDevOpsPipelineDefinitionRecord> listPipelines(AzureDevOpsPipelineDiscoveryInputs inputs);
}
