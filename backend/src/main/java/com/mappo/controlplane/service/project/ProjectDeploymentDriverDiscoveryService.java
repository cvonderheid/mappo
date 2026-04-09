package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProjectAdoBranchDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoPipelineDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoRepositoryDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoServiceConnectionDiscoveryRequest;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.integrations.azuredevops.discovery.AzureDevOpsDiscoveryException;
import com.mappo.controlplane.integrations.azuredevops.discovery.AzureDevOpsDiscoveryGateway;
import com.mappo.controlplane.model.ProjectAdoBranchDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoPipelineDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoRepositoryDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoServiceConnectionDiscoveryResultRecord;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionSecretResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectDeploymentDriverDiscoveryService {

    private final ProjectCatalogService projectCatalogService;
    private final AzureDevOpsDiscoveryGateway azureDevOpsDiscoveryGateway;
    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionSecretResolver providerConnectionSecretResolver;

    public ProjectDeploymentDriverDiscoveryService(
        ProjectCatalogService projectCatalogService,
        AzureDevOpsDiscoveryGateway azureDevOpsDiscoveryGateway,
        ProviderConnectionCatalogService providerConnectionCatalogService,
        ProviderConnectionSecretResolver providerConnectionSecretResolver
    ) {
        this.projectCatalogService = projectCatalogService;
        this.azureDevOpsDiscoveryGateway = azureDevOpsDiscoveryGateway;
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
            return new ProjectAdoPipelineDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                azureDevOpsDiscoveryGateway.discoverPipelines(
                    organization,
                    adoProject,
                    personalAccessToken,
                    nameContains
                )
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsDiscoveryException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, normalize(exception.getMessage()));
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
            return new ProjectAdoRepositoryDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                azureDevOpsDiscoveryGateway.discoverRepositories(
                    organization,
                    adoProject,
                    personalAccessToken,
                    nameContains
                )
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsDiscoveryException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, normalize(exception.getMessage()));
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
            return new ProjectAdoBranchDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                repositoryId,
                repository,
                azureDevOpsDiscoveryGateway.discoverBranches(
                    organization,
                    adoProject,
                    repositoryId,
                    repository,
                    personalAccessToken,
                    nameContains
                )
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsDiscoveryException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, normalize(exception.getMessage()));
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
            return new ProjectAdoServiceConnectionDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                azureDevOpsDiscoveryGateway.discoverServiceConnections(
                    organization,
                    adoProject,
                    personalAccessToken,
                    nameContains
                )
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsDiscoveryException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, normalize(exception.getMessage()));
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
