package com.mappo.controlplane.integrations.azuredevops.discovery;

import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsBranchDefinitionRecord;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsClientException;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsPipelineDefinitionRecord;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsPipelineDiscoveryService;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsProjectDefinitionRecord;
import com.mappo.controlplane.integrations.azuredevops.pipeline.AzureDevOpsRepositoryDefinitionRecord;
import com.mappo.controlplane.model.ProjectAdoBranchRecord;
import com.mappo.controlplane.model.ProjectAdoPipelineRecord;
import com.mappo.controlplane.model.ProjectAdoRepositoryRecord;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HttpAzureDevOpsDiscoveryGateway implements AzureDevOpsDiscoveryGateway {

    private final AzureDevOpsPipelineDiscoveryService pipelineDiscoveryService;

    @Override
    public List<ProviderConnectionAdoProjectRecord> discoverProjects(
        String organizationUrl,
        String personalAccessToken,
        String nameContains
    ) {
        String filter = normalize(nameContains).toLowerCase(Locale.ROOT);
        try {
            return pipelineDiscoveryService
                .discoverProjects(organizationUrl, personalAccessToken)
                .stream()
                .filter(project -> filter.isBlank()
                    || normalize(project.name()).toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator
                    .comparing((AzureDevOpsProjectDefinitionRecord project) -> normalize(project.name()).toLowerCase(Locale.ROOT))
                    .thenComparing(project -> normalize(project.id())))
                .map(project -> new ProviderConnectionAdoProjectRecord(
                    normalize(project.id()),
                    normalize(project.name()),
                    normalize(project.webUrl())
                ))
                .toList();
        } catch (AzureDevOpsClientException exception) {
            throw new AzureDevOpsDiscoveryException(
                "Azure DevOps project discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    @Override
    public List<ProjectAdoRepositoryRecord> discoverRepositories(
        String organization,
        String project,
        String personalAccessToken,
        String nameContains
    ) {
        String filter = normalize(nameContains).toLowerCase(Locale.ROOT);
        try {
            return pipelineDiscoveryService
                .discoverRepositories(organization, project, personalAccessToken)
                .stream()
                .filter(repository -> filter.isBlank()
                    || normalize(repository.name()).toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator
                    .comparing((AzureDevOpsRepositoryDefinitionRecord repository) -> normalize(repository.name()).toLowerCase(Locale.ROOT))
                    .thenComparing(repository -> normalize(repository.id())))
                .map(repository -> new ProjectAdoRepositoryRecord(
                    normalize(repository.id()),
                    normalize(repository.name()),
                    normalize(repository.defaultBranch()),
                    normalize(repository.webUrl()),
                    normalize(repository.remoteUrl())
                ))
                .toList();
        } catch (AzureDevOpsClientException exception) {
            throw new AzureDevOpsDiscoveryException(
                "Azure DevOps repository discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    @Override
    public List<ProjectAdoBranchRecord> discoverBranches(
        String organization,
        String project,
        String repositoryId,
        String repository,
        String personalAccessToken,
        String nameContains
    ) {
        String filter = normalize(nameContains).toLowerCase(Locale.ROOT);
        try {
            return pipelineDiscoveryService
                .discoverBranches(organization, project, repositoryId, repository, personalAccessToken)
                .stream()
                .filter(branch -> filter.isBlank()
                    || normalize(branch.name()).toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator
                    .comparing((AzureDevOpsBranchDefinitionRecord branch) -> normalize(branch.name()).toLowerCase(Locale.ROOT))
                    .thenComparing(branch -> normalize(branch.refName())))
                .map(branch -> new ProjectAdoBranchRecord(
                    normalize(branch.name()),
                    normalize(branch.refName())
                ))
                .toList();
        } catch (AzureDevOpsClientException exception) {
            throw new AzureDevOpsDiscoveryException(
                "Azure DevOps branch discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    @Override
    public List<ProjectAdoPipelineRecord> discoverPipelines(
        String organization,
        String project,
        String personalAccessToken,
        String nameContains
    ) {
        String filter = normalize(nameContains).toLowerCase(Locale.ROOT);
        try {
            return pipelineDiscoveryService
                .discoverPipelines(organization, project, personalAccessToken)
                .stream()
                .filter(pipeline -> filter.isBlank()
                    || normalize(pipeline.name()).toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator
                    .comparing((AzureDevOpsPipelineDefinitionRecord pipeline) -> normalize(pipeline.name()).toLowerCase(Locale.ROOT))
                    .thenComparing(pipeline -> normalize(pipeline.id())))
                .map(pipeline -> new ProjectAdoPipelineRecord(
                    normalize(pipeline.id()),
                    normalize(pipeline.name()),
                    normalize(pipeline.folder()),
                    normalize(pipeline.webUrl())
                ))
                .toList();
        } catch (AzureDevOpsClientException exception) {
            throw new AzureDevOpsDiscoveryException(
                "Azure DevOps pipeline discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
