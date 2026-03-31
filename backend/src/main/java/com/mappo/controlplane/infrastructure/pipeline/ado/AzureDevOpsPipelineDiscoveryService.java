package com.mappo.controlplane.infrastructure.pipeline.ado;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AzureDevOpsPipelineDiscoveryService {

    private final AzureDevOpsPipelineClient pipelineClient;

    public List<AzureDevOpsPipelineDefinitionRecord> discoverPipelines(
        String organization,
        String project,
        String personalAccessToken
    ) {
        String token = normalize(personalAccessToken);
        if (token.isBlank()) {
            throw new IllegalArgumentException("Azure DevOps PAT could not be resolved.");
        }
        return pipelineClient.listPipelines(new AzureDevOpsPipelineDiscoveryInputs(organization, project, token));
    }

    public List<AzureDevOpsRepositoryDefinitionRecord> discoverRepositories(
        String organization,
        String project,
        String personalAccessToken
    ) {
        String token = normalize(personalAccessToken);
        if (token.isBlank()) {
            throw new IllegalArgumentException("Azure DevOps PAT could not be resolved.");
        }
        return pipelineClient.listRepositories(new AzureDevOpsPipelineDiscoveryInputs(organization, project, token));
    }

    public List<AzureDevOpsServiceConnectionDefinitionRecord> discoverServiceConnections(
        String organization,
        String project,
        String personalAccessToken
    ) {
        String token = normalize(personalAccessToken);
        if (token.isBlank()) {
            throw new IllegalArgumentException("Azure DevOps PAT could not be resolved.");
        }
        return pipelineClient.listServiceConnections(
            new AzureDevOpsPipelineDiscoveryInputs(organization, project, token)
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
