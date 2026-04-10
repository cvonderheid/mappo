package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.api.request.ProviderConnectionVerifyRequest;
import com.mappo.controlplane.application.providerconnection.AzureDevOpsProviderConnectionDiscoveryHandler;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectDiscoveryResultRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderConnectionDiscoveryService {

    private final AzureDevOpsProviderConnectionDiscoveryHandler azureDevOpsProviderConnectionDiscoveryHandler;

    public ProviderConnectionAdoProjectDiscoveryResultRecord verifyAdoConnection(
        ProviderConnectionVerifyRequest request,
        String nameContains
    ) {
        return azureDevOpsProviderConnectionDiscoveryHandler.verifyAdoConnection(request, nameContains);
    }

    public ProviderConnectionAdoProjectDiscoveryResultRecord discoverAdoProjects(
        String connectionId,
        String nameContains
    ) {
        return azureDevOpsProviderConnectionDiscoveryHandler.discoverAdoProjects(connectionId, nameContains);
    }

    public ProviderConnectionAdoProjectDiscoveryResultRecord validateForSave(ProviderConnectionMutationRecord mutation) {
        return azureDevOpsProviderConnectionDiscoveryHandler.validateForSave(mutation);
    }
}
