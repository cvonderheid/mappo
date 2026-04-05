package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProjectAdoBranchDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoPipelineDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoRepositoryDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoServiceConnectionDiscoveryRequest;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsBranchDefinitionRecord;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsClientException;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDefinitionRecord;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDiscoveryService;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsRepositoryDefinitionRecord;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsServiceConnectionDefinitionRecord;
import com.mappo.controlplane.model.ProjectAdoBranchDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoBranchRecord;
import com.mappo.controlplane.model.ProjectAdoPipelineDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoPipelineRecord;
import com.mappo.controlplane.model.ProjectAdoRepositoryDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoRepositoryRecord;
import com.mappo.controlplane.model.ProjectAdoServiceConnectionDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoServiceConnectionRecord;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionSecretResolver;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectDeploymentDriverDiscoveryService {

    private final ProjectCatalogService projectCatalogService;
    private final AzureDevOpsPipelineDiscoveryService pipelineDiscoveryService;
    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionSecretResolver providerConnectionSecretResolver;

    public ProjectDeploymentDriverDiscoveryService(
        ProjectCatalogService projectCatalogService,
        AzureDevOpsPipelineDiscoveryService pipelineDiscoveryService,
        ProviderConnectionCatalogService providerConnectionCatalogService,
        ProviderConnectionSecretResolver providerConnectionSecretResolver
    ) {
        this.projectCatalogService = projectCatalogService;
        this.pipelineDiscoveryService = pipelineDiscoveryService;
        this.providerConnectionCatalogService = providerConnectionCatalogService;
        this.providerConnectionSecretResolver = providerConnectionSecretResolver;
    }

    public ProjectAdoPipelineDiscoveryResultRecord discoverAdoPipelines(
        String projectId,
        ProjectAdoPipelineDiscoveryRequest request
    ) {
        ProjectDefinition definition = projectCatalogService.getRequired(projectId);
        if (definition.deploymentDriver() != ProjectDeploymentDriverType.pipeline_trigger) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Project deployment driver must be pipeline_trigger for Azure DevOps pipeline discovery."
            );
        }

        if (!(definition.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig config)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Project deployment driver configuration is not pipeline_trigger-compatible."
            );
        }

        String pipelineSystem = normalize(config.pipelineSystem());
        if (!"azure_devops".equalsIgnoreCase(pipelineSystem)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "This project must use the Azure DevOps pipeline system before MAPPO can discover Azure DevOps resources."
            );
        }

        String organization = firstNonBlank(
            request == null ? null : request.organization(),
            config.organization()
        );
        String adoProject = firstNonBlank(
            request == null ? null : request.project(),
            config.project()
        );
        String personalAccessToken = resolveAdoPersonalAccessToken(
            definition,
            request == null ? null : request.providerConnectionId()
        );
        String nameContains = normalize(request == null ? null : request.nameContains());

        if (organization.isBlank() || adoProject.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Select an Azure DevOps project before MAPPO can discover pipelines."
            );
        }

        try {
            List<ProjectAdoPipelineRecord> pipelines = pipelineDiscoveryService
                .discoverPipelines(organization, adoProject, personalAccessToken)
                .stream()
                .filter(pipeline -> nameContains.isBlank()
                    || normalize(pipeline.name()).toLowerCase(Locale.ROOT).contains(nameContains.toLowerCase(Locale.ROOT)))
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
            return new ProjectAdoPipelineDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                pipelines
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsClientException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Azure DevOps pipeline discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    public ProjectAdoRepositoryDiscoveryResultRecord discoverAdoRepositories(
        String projectId,
        ProjectAdoRepositoryDiscoveryRequest request
    ) {
        ProjectDefinition definition = projectCatalogService.getRequired(projectId);
        PipelineTriggerDriverConfig config = requireAdoPipelineTriggerConfig(definition);

        String organization = firstNonBlank(
            request == null ? null : request.organization(),
            config.organization()
        );
        String adoProject = firstNonBlank(
            request == null ? null : request.project(),
            config.project()
        );
        String personalAccessToken = resolveAdoPersonalAccessToken(
            definition,
            request == null ? null : request.providerConnectionId()
        );
        String nameContains = normalize(request == null ? null : request.nameContains());

        if (organization.isBlank() || adoProject.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Select an Azure DevOps project before MAPPO can discover repositories."
            );
        }

        try {
            List<ProjectAdoRepositoryRecord> repositories = pipelineDiscoveryService
                .discoverRepositories(organization, adoProject, personalAccessToken)
                .stream()
                .filter(repository -> nameContains.isBlank()
                    || normalize(repository.name()).toLowerCase(Locale.ROOT).contains(nameContains.toLowerCase(Locale.ROOT)))
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
            return new ProjectAdoRepositoryDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                repositories
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsClientException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Azure DevOps repository discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    public ProjectAdoBranchDiscoveryResultRecord discoverAdoBranches(
        String projectId,
        ProjectAdoBranchDiscoveryRequest request
    ) {
        ProjectDefinition definition = projectCatalogService.getRequired(projectId);
        PipelineTriggerDriverConfig config = requireAdoPipelineTriggerConfig(definition);

        String organization = firstNonBlank(
            request == null ? null : request.organization(),
            config.organization()
        );
        String adoProject = firstNonBlank(
            request == null ? null : request.project(),
            config.project()
        );
        String repositoryId = normalize(request == null ? null : request.repositoryId());
        String repository = firstNonBlank(
            request == null ? null : request.repository(),
            config.repository()
        );
        String personalAccessToken = resolveAdoPersonalAccessToken(
            definition,
            request == null ? null : request.providerConnectionId()
        );
        String nameContains = normalize(request == null ? null : request.nameContains());

        if (organization.isBlank() || adoProject.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Select an Azure DevOps project before MAPPO can discover branches."
            );
        }
        if (repositoryId.isBlank() && repository.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Select an Azure DevOps repository before MAPPO can discover branches."
            );
        }

        try {
            List<ProjectAdoBranchRecord> branches = pipelineDiscoveryService
                .discoverBranches(organization, adoProject, repositoryId, repository, personalAccessToken)
                .stream()
                .filter(branch -> nameContains.isBlank()
                    || normalize(branch.name()).toLowerCase(Locale.ROOT).contains(nameContains.toLowerCase(Locale.ROOT)))
                .sorted(Comparator
                    .comparing((AzureDevOpsBranchDefinitionRecord branch) -> normalize(branch.name()).toLowerCase(Locale.ROOT))
                    .thenComparing(branch -> normalize(branch.refName())))
                .map(branch -> new ProjectAdoBranchRecord(
                    normalize(branch.name()),
                    normalize(branch.refName())
                ))
                .toList();
            return new ProjectAdoBranchDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                repositoryId,
                repository,
                branches
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsClientException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Azure DevOps branch discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    public ProjectAdoServiceConnectionDiscoveryResultRecord discoverAdoServiceConnections(
        String projectId,
        ProjectAdoServiceConnectionDiscoveryRequest request
    ) {
        ProjectDefinition definition = projectCatalogService.getRequired(projectId);
        PipelineTriggerDriverConfig config = requireAdoPipelineTriggerConfig(definition);

        String organization = firstNonBlank(
            request == null ? null : request.organization(),
            config.organization()
        );
        String adoProject = firstNonBlank(
            request == null ? null : request.project(),
            config.project()
        );
        String personalAccessToken = resolveAdoPersonalAccessToken(
            definition,
            request == null ? null : request.providerConnectionId()
        );
        String nameContains = normalize(request == null ? null : request.nameContains());

        if (organization.isBlank() || adoProject.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Select an Azure DevOps project before MAPPO can discover Azure service connections."
            );
        }

        try {
            List<ProjectAdoServiceConnectionRecord> serviceConnections = pipelineDiscoveryService
                .discoverServiceConnections(organization, adoProject, personalAccessToken)
                .stream()
                .filter(connection -> nameContains.isBlank()
                    || normalize(connection.name()).toLowerCase(Locale.ROOT).contains(nameContains.toLowerCase(Locale.ROOT)))
                .sorted(Comparator
                    .comparing((AzureDevOpsServiceConnectionDefinitionRecord connection) -> normalize(connection.name()).toLowerCase(Locale.ROOT))
                    .thenComparing(connection -> normalize(connection.id())))
                .map(connection -> new ProjectAdoServiceConnectionRecord(
                    normalize(connection.id()),
                    normalize(connection.name()),
                    normalize(connection.type()),
                    normalize(connection.webUrl())
                ))
                .toList();
            return new ProjectAdoServiceConnectionDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                serviceConnections
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsClientException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Azure DevOps service connection discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    private PipelineTriggerDriverConfig requireAdoPipelineTriggerConfig(ProjectDefinition definition) {
        if (definition.deploymentDriver() != ProjectDeploymentDriverType.pipeline_trigger) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Project deployment driver must be pipeline_trigger for Azure DevOps pipeline discovery."
            );
        }

        if (!(definition.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig config)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Project deployment driver configuration is not pipeline_trigger-compatible."
            );
        }

        String pipelineSystem = normalize(config.pipelineSystem());
        if (!"azure_devops".equalsIgnoreCase(pipelineSystem)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "This project must use the Azure DevOps pipeline system before MAPPO can discover Azure DevOps resources."
            );
        }
        return config;
    }

    private String resolveAdoPersonalAccessToken(ProjectDefinition definition, String requestProviderConnectionId) {
        String providerConnectionId = firstNonBlank(requestProviderConnectionId, definition.providerConnectionId());
        if (providerConnectionId.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Select a deployment connection before MAPPO can discover Azure DevOps resources."
            );
        }
        ProviderConnectionRecord connection = providerConnectionCatalogService.getRequired(providerConnectionId);
        if (connection.provider() == null || !"azure_devops".equalsIgnoreCase(connection.provider().name())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Deployment connection " + providerConnectionId + " is not configured for Azure DevOps."
            );
        }
        String token = normalize(providerConnectionSecretResolver.resolvePersonalAccessToken(connection));
        if (token.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Azure DevOps PAT could not be resolved from deployment connection " + providerConnectionId + ". Open Admin → Deployment Connections and verify its credential source."
            );
        }
        return token;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
