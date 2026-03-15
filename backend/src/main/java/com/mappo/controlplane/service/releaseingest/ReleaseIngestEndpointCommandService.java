package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.api.request.ReleaseIngestEndpointCreateRequest;
import com.mappo.controlplane.api.request.ReleaseIngestEndpointPatchRequest;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.repository.ReleaseIngestEndpointCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReleaseIngestEndpointCommandService {

    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ReleaseIngestEndpointMutationService releaseIngestEndpointMutationService;
    private final ReleaseIngestEndpointCommandRepository releaseIngestEndpointCommandRepository;

    @Transactional
    public ReleaseIngestEndpointRecord createEndpoint(ReleaseIngestEndpointCreateRequest request) {
        ReleaseIngestEndpointMutationRecord mutation = releaseIngestEndpointMutationService.fromCreate(request);
        releaseIngestEndpointCommandRepository.createEndpoint(mutation);
        return releaseIngestEndpointCatalogService.getRequired(mutation.id());
    }

    @Transactional
    public ReleaseIngestEndpointRecord patchEndpoint(String endpointId, ReleaseIngestEndpointPatchRequest patchRequest) {
        ReleaseIngestEndpointRecord current = releaseIngestEndpointCatalogService.getRequired(endpointId);
        ReleaseIngestEndpointMutationRecord mutation = releaseIngestEndpointMutationService.fromPatch(current, patchRequest);
        releaseIngestEndpointCommandRepository.updateEndpoint(mutation);
        return releaseIngestEndpointCatalogService.getRequired(mutation.id());
    }

    @Transactional
    public void deleteEndpoint(String endpointId) {
        releaseIngestEndpointCommandRepository.deleteEndpoint(endpointId);
    }
}
