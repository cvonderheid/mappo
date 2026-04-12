package com.mappo.controlplane.integrations.azuredevops.discovery;

import com.mappo.controlplane.model.ProjectAdoBranchRecord;
import com.mappo.controlplane.model.ProjectAdoPipelineRecord;
import com.mappo.controlplane.model.ProjectAdoRepositoryRecord;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectRecord;
import java.util.List;

public interface AzureDevOpsDiscoveryGateway {

    List<ProviderConnectionAdoProjectRecord> discoverProjects(
        String organizationUrl,
        String personalAccessToken,
        String nameContains
    );

    List<ProjectAdoRepositoryRecord> discoverRepositories(
        String organization,
        String project,
        String personalAccessToken,
        String nameContains
    );

    List<ProjectAdoBranchRecord> discoverBranches(
        String organization,
        String project,
        String repositoryId,
        String repository,
        String personalAccessToken,
        String nameContains
    );

    List<ProjectAdoPipelineRecord> discoverPipelines(
        String organization,
        String project,
        String personalAccessToken,
        String nameContains
    );
}
