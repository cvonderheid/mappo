package com.mappo.controlplane.infrastructure.pipeline.ado;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AzureDevOpsPipelineDiscoveryService {

    private final AzureDevOpsPipelineClient pipelineClient;

    public List<AzureDevOpsProjectDefinitionRecord> discoverProjects(
        String organization,
        String personalAccessToken
    ) {
        String token = normalize(personalAccessToken);
        if (token.isBlank()) {
            throw new IllegalArgumentException("Azure DevOps PAT could not be resolved for the selected deployment connection.");
        }
        String normalizedOrganization = normalize(organization);
        if (normalizedOrganization.isBlank()) {
            throw new IllegalArgumentException(
                "An Azure DevOps account, project, or repository URL is required before MAPPO can discover Azure DevOps projects. Paste any Azure DevOps URL from the account MAPPO should browse."
            );
        }
        return pipelineClient.listProjects(normalizedOrganization, token);
    }

    public List<AzureDevOpsPipelineDefinitionRecord> discoverPipelines(
        String organization,
        String project,
        String personalAccessToken
    ) {
        String token = normalize(personalAccessToken);
        if (token.isBlank()) {
            throw new IllegalArgumentException("Azure DevOps PAT could not be resolved for the selected deployment connection.");
        }
        return pipelineClient.listPipelines(new AzureDevOpsPipelineDiscoveryInputs(organization, project, token));
    }

    public List<AzureDevOpsBranchDefinitionRecord> discoverBranches(
        String organization,
        String project,
        String repositoryId,
        String repository,
        String personalAccessToken
    ) {
        String token = normalize(personalAccessToken);
        if (token.isBlank()) {
            throw new IllegalArgumentException("Azure DevOps PAT could not be resolved for the selected deployment connection.");
        }
        if (normalize(repositoryId).isBlank() && normalize(repository).isBlank()) {
            throw new IllegalArgumentException("Select an Azure DevOps repository before MAPPO can discover branches.");
        }
        return pipelineClient.listBranches(
            new AzureDevOpsBranchDiscoveryInputs(organization, project, repositoryId, repository, token)
        );
    }

    public List<AzureDevOpsRepositoryDefinitionRecord> discoverRepositories(
        String organization,
        String project,
        String personalAccessToken
    ) {
        String token = normalize(personalAccessToken);
        if (token.isBlank()) {
            throw new IllegalArgumentException("Azure DevOps PAT could not be resolved for the selected deployment connection.");
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
            throw new IllegalArgumentException("Azure DevOps PAT could not be resolved for the selected deployment connection.");
        }
        return pipelineClient.listServiceConnections(
            new AzureDevOpsPipelineDiscoveryInputs(organization, project, token)
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
