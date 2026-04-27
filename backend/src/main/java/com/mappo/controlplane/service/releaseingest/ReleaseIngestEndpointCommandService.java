package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseIngestEndpointCreateRequest;
import com.mappo.controlplane.api.request.ReleaseIngestEndpointPatchRequest;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.persistence.releaseingest.ReleaseIngestEndpointCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReleaseIngestEndpointCommandService {

    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ReleaseIngestEndpointMutationService releaseIngestEndpointMutationService;
    private final ReleaseIngestEndpointCommandRepository releaseIngestEndpointCommandRepository;
    private final ReleaseIngestSecretResolver releaseIngestSecretResolver;

    @Transactional
    public ReleaseIngestEndpointRecord createEndpoint(ReleaseIngestEndpointCreateRequest request) {
        ReleaseIngestEndpointMutationRecord mutation = releaseIngestEndpointMutationService.fromCreate(request);
        validateForSave(mutation);
        String createdId = releaseIngestEndpointCommandRepository.createEndpoint(mutation);
        return releaseIngestEndpointCatalogService.getRequired(createdId);
    }

    @Transactional
    public ReleaseIngestEndpointRecord patchEndpoint(String endpointId, ReleaseIngestEndpointPatchRequest patchRequest) {
        ReleaseIngestEndpointRecord current = releaseIngestEndpointCatalogService.getRequired(endpointId);
        ReleaseIngestEndpointMutationRecord mutation = releaseIngestEndpointMutationService.fromPatch(current, patchRequest);
        validateForSave(mutation);
        releaseIngestEndpointCommandRepository.updateEndpoint(mutation);
        return releaseIngestEndpointCatalogService.getRequired(mutation.id());
    }

    @Transactional
    public void deleteEndpoint(String endpointId) {
        releaseIngestEndpointCommandRepository.deleteEndpoint(endpointId);
    }

    private void validateForSave(ReleaseIngestEndpointMutationRecord mutation) {
        if (mutation == null || !mutation.enabled()) {
            return;
        }
        String resolvedSecret = releaseIngestSecretResolver.resolveConfiguredSecret(
            mutation.provider(),
            mutation.secretRef()
        );
        if (resolvedSecret.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Webhook secret could not be resolved. Configure the provider default secret on the backend runtime, use env:VAR_NAME, or use kv:secret-name backed by MAPPO's Azure Key Vault."
            );
        }
    }
}
