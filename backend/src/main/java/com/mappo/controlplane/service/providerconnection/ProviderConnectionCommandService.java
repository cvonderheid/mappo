package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.api.request.ProviderConnectionCreateRequest;
import com.mappo.controlplane.api.request.ProviderConnectionPatchRequest;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectDiscoveryResultRecord;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.repository.ProviderConnectionCommandRepository;
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
        providerConnectionCommandRepository.createConnection(mutation);
        providerConnectionCommandRepository.replaceDiscoveredAdoProjects(mutation.id(), discoveryResult.projects());
        return providerConnectionCatalogService.getRequired(mutation.id());
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
        providerConnectionCommandRepository.updateConnection(mutation);
        providerConnectionCommandRepository.replaceDiscoveredAdoProjects(mutation.id(), discoveryResult.projects());
        return providerConnectionCatalogService.getRequired(mutation.id());
    }

    @Transactional
    public void deleteConnection(String connectionId) {
        providerConnectionCommandRepository.deleteConnection(connectionId);
    }
}
