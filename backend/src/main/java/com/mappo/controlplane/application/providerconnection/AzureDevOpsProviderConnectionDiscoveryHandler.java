package com.mappo.controlplane.application.providerconnection;

import com.mappo.controlplane.api.request.ProviderConnectionVerifyRequest;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectDiscoveryResultRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionMutationRecord;

public interface AzureDevOpsProviderConnectionDiscoveryHandler {

    ProviderConnectionAdoProjectDiscoveryResultRecord verifyAdoConnection(
        ProviderConnectionVerifyRequest request,
        String nameContains
    );

    ProviderConnectionAdoProjectDiscoveryResultRecord discoverAdoProjects(
        String connectionId,
        String nameContains
    );

    ProviderConnectionAdoProjectDiscoveryResultRecord validateForSave(
        ProviderConnectionMutationRecord mutation
    );
}
