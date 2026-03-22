package com.mappo.controlplane.infrastructure.pipeline.ado;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AzureDevOpsPipelineDiscoveryService {

    private final AzureDevOpsSecretResolver secretResolver;
    private final AzureDevOpsPipelineClient pipelineClient;

    public List<AzureDevOpsPipelineDefinitionRecord> discoverPipelines(
        String organization,
        String project,
        String personalAccessTokenReference
    ) {
        String token = secretResolver.resolvePersonalAccessToken(personalAccessTokenReference);
        if (token.isBlank()) {
            throw new IllegalArgumentException(
                "Azure DevOps PAT could not be resolved. Use server-managed token (mappo.azure-devops.personal-access-token / MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN) or provide an inline demo token."
            );
        }
        return pipelineClient.listPipelines(new AzureDevOpsPipelineDiscoveryInputs(organization, project, token));
    }

    public List<AzureDevOpsServiceConnectionDefinitionRecord> discoverServiceConnections(
        String organization,
        String project,
        String personalAccessTokenReference
    ) {
        String token = secretResolver.resolvePersonalAccessToken(personalAccessTokenReference);
        if (token.isBlank()) {
            throw new IllegalArgumentException(
                "Azure DevOps PAT could not be resolved. Use server-managed token (mappo.azure-devops.personal-access-token / MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN) or provide an inline demo token."
            );
        }
        return pipelineClient.listServiceConnections(
            new AzureDevOpsPipelineDiscoveryInputs(organization, project, token)
        );
    }
}
