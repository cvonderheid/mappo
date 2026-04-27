package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.api.request.ProviderConnectionCreateRequest;
import com.mappo.controlplane.api.request.ProviderConnectionPatchRequest;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectDiscoveryResultRecord;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.persistence.providerconnection.ProviderConnectionCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderConnectionCommandService {

    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionMutationService providerConnectionMutationService;
    private final ProviderConnectionDiscoveryService providerConnectionDiscoveryService;
    private final ProviderConnectionCommandRepository providerConnectionCommandRepository;

    @Transactional
    public ProviderConnectionRecord createConnection(ProviderConnectionCreateRequest request) {
        ProviderConnectionMutationRecord mutation = providerConnectionMutationService.fromCreate(request);
        ProviderConnectionAdoProjectDiscoveryResultRecord discoveryResult =
            providerConnectionDiscoveryService.validateForSave(mutation);
        ProviderConnectionMutationRecord verifiedMutation = applyDiscoveryResult(mutation, discoveryResult);
        String createdId = providerConnectionCommandRepository.createConnection(verifiedMutation);
        providerConnectionCommandRepository.replaceDiscoveredAdoProjects(
            createdId,
            discoveryResult.projects()
        );
        return providerConnectionCatalogService.getRequired(createdId);
    }

    @Transactional
    public ProviderConnectionRecord patchConnection(
        String connectionId,
        ProviderConnectionPatchRequest patchRequest
    ) {
        ProviderConnectionRecord current = providerConnectionCatalogService.getRequired(connectionId);
        ProviderConnectionMutationRecord mutation = providerConnectionMutationService.fromPatch(current, patchRequest);
        ProviderConnectionAdoProjectDiscoveryResultRecord discoveryResult =
            providerConnectionDiscoveryService.validateForSave(mutation);
        ProviderConnectionMutationRecord verifiedMutation = applyDiscoveryResult(mutation, discoveryResult);
        providerConnectionCommandRepository.updateConnection(verifiedMutation);
        providerConnectionCommandRepository.replaceDiscoveredAdoProjects(
            verifiedMutation.id(),
            discoveryResult.projects()
        );
        return providerConnectionCatalogService.getRequired(verifiedMutation.id());
    }

    @Transactional
    public void deleteConnection(String connectionId) {
        providerConnectionCommandRepository.deleteConnection(connectionId);
    }

    private ProviderConnectionMutationRecord applyDiscoveryResult(
        ProviderConnectionMutationRecord mutation,
        ProviderConnectionAdoProjectDiscoveryResultRecord discoveryResult
    ) {
        String normalizedOrganizationUrl =
            discoveryResult == null || discoveryResult.organizationUrl() == null
                ? ""
                : discoveryResult.organizationUrl().trim();
        if (normalizedOrganizationUrl.isBlank()) {
            return mutation;
        }
        return new ProviderConnectionMutationRecord(
            mutation.id(),
            mutation.name(),
            mutation.provider(),
            mutation.enabled(),
            normalizedOrganizationUrl,
            mutation.personalAccessTokenRef()
        );
    }
}
