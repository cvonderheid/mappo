package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProviderConnectionVerifyRequest;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsClientException;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDiscoveryService;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsProjectDefinitionRecord;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectDiscoveryResultRecord;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectRecord;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderConnectionDiscoveryService {

    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionMutationService providerConnectionMutationService;
    private final ProviderConnectionSecretResolver providerConnectionSecretResolver;
    private final AzureDevOpsPipelineDiscoveryService pipelineDiscoveryService;

    public ProviderConnectionAdoProjectDiscoveryResultRecord verifyAdoConnection(
        ProviderConnectionVerifyRequest request,
        String nameContains
    ) {
        ProviderConnectionMutationRecord draft = providerConnectionMutationService.fromVerification(request);
        return discoverAdoProjects(draft, nameContains);
    }

    public ProviderConnectionAdoProjectDiscoveryResultRecord discoverAdoProjects(
        String connectionId,
        String nameContains
    ) {
        ProviderConnectionRecord connection = providerConnectionCatalogService.getRequired(connectionId);
        ProviderConnectionMutationRecord mutation = new ProviderConnectionMutationRecord(
            normalize(connection.id()),
            normalize(connection.name()),
            connection.provider(),
            connection.enabled(),
            normalize(connection.organizationUrl()),
            normalize(connection.personalAccessTokenRef())
        );
        return discoverAdoProjects(mutation, nameContains);
    }

    public void validateForSave(ProviderConnectionMutationRecord mutation) {
        if (mutation == null || mutation.provider() == null || !"azure_devops".equalsIgnoreCase(mutation.provider().name()) || !mutation.enabled()) {
            return;
        }
        try {
            discoverAdoProjects(mutation, null);
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.BAD_GATEWAY) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Azure DevOps provider connection validation failed: " + normalize(exception.getMessage())
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
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Selected provider connection is not an Azure DevOps connection."
            );
        }
        String organizationUrl = normalize(mutation.organizationUrl());
        if (organizationUrl.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Azure DevOps URL is required before MAPPO can discover Azure DevOps projects."
            );
        }
        String personalAccessToken = providerConnectionSecretResolver.resolvePersonalAccessToken(
            mutation.provider(),
            mutation.personalAccessTokenRef()
        );
        if (personalAccessToken.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Azure DevOps PAT could not be resolved. Set "
                    + ProviderConnectionSecretResolver.AZURE_DEVOPS_PAT_SECRET_REF
                    + " on the backend runtime or use env:VAR_NAME."
            );
        }

        String filter = normalize(nameContains).toLowerCase(Locale.ROOT);
        try {
            List<ProviderConnectionAdoProjectRecord> projects = pipelineDiscoveryService
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
            return new ProviderConnectionAdoProjectDiscoveryResultRecord(
                normalize(mutation.id()),
                organizationUrl,
                projects
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsClientException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Azure DevOps project discovery failed: " + normalize(exception.responseBody())
            );
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
