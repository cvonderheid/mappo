package com.mappo.controlplane.integrations.azuredevops.connection;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProviderConnectionVerifyRequest;
import com.mappo.controlplane.application.providerconnection.AzureDevOpsProviderConnectionDiscoveryHandler;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.integrations.azuredevops.discovery.AzureDevOpsDiscoveryException;
import com.mappo.controlplane.integrations.azuredevops.discovery.AzureDevOpsDiscoveryGateway;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.PipelineTriggerDriverConfig;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectDiscoveryResultRecord;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectRecord;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.persistence.providerconnection.ProviderConnectionCommandRepository;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionMutationRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionMutationService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionSecretResolver;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DefaultAzureDevOpsProviderConnectionDiscoveryHandler implements AzureDevOpsProviderConnectionDiscoveryHandler {

    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionMutationService providerConnectionMutationService;
    private final ProviderConnectionSecretResolver providerConnectionSecretResolver;
    private final AzureDevOpsDiscoveryGateway azureDevOpsDiscoveryGateway;
    private final ProviderConnectionCommandRepository providerConnectionCommandRepository;
    private final ProjectCatalogService projectCatalogService;

    public DefaultAzureDevOpsProviderConnectionDiscoveryHandler(
        ProviderConnectionCatalogService providerConnectionCatalogService,
        ProviderConnectionMutationService providerConnectionMutationService,
        ProviderConnectionSecretResolver providerConnectionSecretResolver,
        AzureDevOpsDiscoveryGateway azureDevOpsDiscoveryGateway,
        ProviderConnectionCommandRepository providerConnectionCommandRepository,
        ProjectCatalogService projectCatalogService
    ) {
        this.providerConnectionCatalogService = providerConnectionCatalogService;
        this.providerConnectionMutationService = providerConnectionMutationService;
        this.providerConnectionSecretResolver = providerConnectionSecretResolver;
        this.azureDevOpsDiscoveryGateway = azureDevOpsDiscoveryGateway;
        this.providerConnectionCommandRepository = providerConnectionCommandRepository;
        this.projectCatalogService = projectCatalogService;
    }

    @Override
    public ProviderConnectionAdoProjectDiscoveryResultRecord verifyAdoConnection(
        ProviderConnectionVerifyRequest request,
        String nameContains
    ) {
        ProviderConnectionMutationRecord draft = providerConnectionMutationService.fromVerification(request);
        return discoverAdoProjects(draft, nameContains);
    }

    @Override
    public ProviderConnectionAdoProjectDiscoveryResultRecord discoverAdoProjects(
        String connectionId,
        String nameContains
    ) {
        ProviderConnectionRecord connection = providerConnectionCatalogService.getRequired(connectionId);
        String inferredOrganizationUrl = inferOrganizationUrl(connection);
        ProviderConnectionMutationRecord mutation = new ProviderConnectionMutationRecord(
            normalize(connection.id()),
            normalize(connection.name()),
            connection.provider(),
            connection.enabled(),
            inferredOrganizationUrl,
            normalize(connection.personalAccessTokenRef())
        );
        ProviderConnectionAdoProjectDiscoveryResultRecord result = discoverAdoProjects(mutation, nameContains);
        persistDiscoveryState(connection, mutation, result);
        return result;
    }

    @Override
    public ProviderConnectionAdoProjectDiscoveryResultRecord validateForSave(ProviderConnectionMutationRecord mutation) {
        if (mutation == null || mutation.provider() == null || !"azure_devops".equalsIgnoreCase(mutation.provider().name()) || !mutation.enabled()) {
            return new ProviderConnectionAdoProjectDiscoveryResultRecord(
                normalize(mutation == null ? "" : mutation.id()),
                normalize(mutation == null ? "" : mutation.organizationUrl()),
                List.of()
            );
        }
        try {
            return discoverAdoProjects(mutation, null);
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.BAD_GATEWAY) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Azure DevOps deployment connection verification failed. " + normalize(exception.getMessage())
                );
            }
            throw exception;
        }
    }

    private ProviderConnectionAdoProjectDiscoveryResultRecord discoverAdoProjects(
        ProviderConnectionMutationRecord mutation,
        String nameContains
    ) {
        if (mutation.provider() == null || !"azure_devops".equalsIgnoreCase(mutation.provider().name())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selected deployment connection is not configured for Azure DevOps.");
        }
        String organizationUrl = normalize(mutation.organizationUrl());
        if (organizationUrl.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "An Azure DevOps account, project, or repository URL is required before MAPPO can verify this deployment connection and discover Azure DevOps projects. Paste any Azure DevOps URL from the account MAPPO should browse."
            );
        }
        String personalAccessToken = providerConnectionSecretResolver.resolvePersonalAccessToken(
            mutation.provider(),
            mutation.personalAccessTokenRef()
        );
        if (personalAccessToken.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Azure DevOps PAT could not be resolved for this deployment connection. Configure "
                    + ProviderConnectionSecretResolver.AZURE_DEVOPS_PAT_SECRET_REF
                    + " on the backend runtime, use env:VAR_NAME, or use kv:secret-name backed by MAPPO's Azure Key Vault."
            );
        }

        String filter = normalize(nameContains).toLowerCase(Locale.ROOT);
        try {
            List<ProviderConnectionAdoProjectRecord> projects = azureDevOpsDiscoveryGateway.discoverProjects(
                organizationUrl,
                personalAccessToken,
                filter
            );
            return new ProviderConnectionAdoProjectDiscoveryResultRecord(normalize(mutation.id()), organizationUrl, projects);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsDiscoveryException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, normalize(exception.getMessage()));
        }
    }

    private String inferOrganizationUrl(ProviderConnectionRecord connection) {
        String persisted = normalize(connection.organizationUrl());
        String normalizedPersisted = deriveOrganizationUrlFromProjectUrl(persisted);
        if (!normalizedPersisted.isBlank()) {
            return normalizedPersisted;
        }
        if (!persisted.isBlank()) {
            return persisted;
        }
        List<String> discoveredProjectOrganizations = connection.discoveredProjects() == null
            ? List.of()
            : connection.discoveredProjects()
                .stream()
                .map(ProviderConnectionAdoProjectRecord::webUrl)
                .map(this::deriveOrganizationUrlFromProjectUrl)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (discoveredProjectOrganizations.size() == 1) {
            return discoveredProjectOrganizations.getFirst();
        }
        if (discoveredProjectOrganizations.size() > 1) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Deployment connection "
                    + normalize(connection.id())
                    + " has cached Azure DevOps projects from multiple account URLs. Open Admin → Deployment Connections, edit it, and verify the correct Azure DevOps account URL."
            );
        }
        List<String> linkedProjectOrganizations = projectCatalogService.listProjects()
            .stream()
            .filter(project -> normalize(project.providerConnectionId()).equals(normalize(connection.id())))
            .map(this::pipelineTriggerOrganization)
            .map(this::normalize)
            .filter(value -> !value.isBlank())
            .distinct()
            .sorted()
            .toList();
        if (linkedProjectOrganizations.isEmpty()) {
            return "";
        }
        if (linkedProjectOrganizations.size() > 1) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Deployment connection "
                    + normalize(connection.id())
                    + " is linked to multiple Azure DevOps account URLs. Open Admin → Deployment Connections, edit it, and verify the correct Azure DevOps account URL."
            );
        }
        return linkedProjectOrganizations.getFirst();
    }

    private String deriveOrganizationUrlFromProjectUrl(String projectUrl) {
        String normalized = normalize(projectUrl);
        if (normalized.isBlank()) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            String host = normalize(uri.getHost()).toLowerCase(Locale.ROOT);
            if (host.isBlank()) {
                return "";
            }
            String scheme = normalize(uri.getScheme()).isBlank() ? "https" : normalize(uri.getScheme());
            if ("dev.azure.com".equals(host)) {
                String[] segments = normalize(uri.getPath()).split("/");
                for (String segment : segments) {
                    if (!segment.isBlank()) {
                        return scheme + "://" + host + "/" + segment;
                    }
                }
                return "";
            }
            if (host.endsWith(".visualstudio.com")) {
                return scheme + "://" + host;
            }
            return "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private String pipelineTriggerOrganization(ProjectDefinition project) {
        if (!(project.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig config)) {
            return "";
        }
        if (config.pipelineSystem() == null || !"azure_devops".equalsIgnoreCase(config.pipelineSystem())) {
            return "";
        }
        return normalize(config.organization());
    }

    private void persistDiscoveryState(
        ProviderConnectionRecord connection,
        ProviderConnectionMutationRecord mutation,
        ProviderConnectionAdoProjectDiscoveryResultRecord result
    ) {
        String persistedOrganizationUrl = normalize(connection.organizationUrl());
        String discoveredOrganizationUrl = normalize(result.organizationUrl());
        boolean organizationChanged = !discoveredOrganizationUrl.isBlank() && !discoveredOrganizationUrl.equals(persistedOrganizationUrl);
        if (organizationChanged) {
            providerConnectionCommandRepository.updateConnection(new ProviderConnectionMutationRecord(
                normalize(connection.id()),
                normalize(connection.name()),
                connection.provider(),
                connection.enabled(),
                discoveredOrganizationUrl,
                normalize(connection.personalAccessTokenRef())
            ));
        }
        providerConnectionCommandRepository.replaceDiscoveredAdoProjects(normalize(mutation.id()), result.projects());
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
