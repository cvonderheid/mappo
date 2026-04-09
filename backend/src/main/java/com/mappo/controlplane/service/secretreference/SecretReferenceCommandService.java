package com.mappo.controlplane.service.secretreference;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.SecretReferenceCreateRequest;
import com.mappo.controlplane.api.request.SecretReferencePatchRequest;
import com.mappo.controlplane.model.SecretReferenceRecord;
import com.mappo.controlplane.repository.SecretReferenceCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SecretReferenceCommandService {

    private final SecretReferenceCatalogService secretReferenceCatalogService;
    private final SecretReferenceMutationService secretReferenceMutationService;
    private final SecretReferenceCommandRepository secretReferenceCommandRepository;

    @Transactional
    public SecretReferenceRecord createSecretReference(SecretReferenceCreateRequest request) {
        SecretReferenceMutationRecord mutation = secretReferenceMutationService.fromCreate(request);
        secretReferenceCommandRepository.createSecretReference(mutation);
        return secretReferenceCatalogService.getRequired(mutation.id());
    }

    @Transactional
    public SecretReferenceRecord patchSecretReference(String secretReferenceId, SecretReferencePatchRequest request) {
        SecretReferenceRecord current = secretReferenceCatalogService.getRequired(secretReferenceId);
        SecretReferenceMutationRecord mutation = secretReferenceMutationService.fromPatch(current, request);
        secretReferenceCommandRepository.updateSecretReference(mutation);
        return secretReferenceCatalogService.getRequired(mutation.id());
    }

    @Transactional
    public void deleteSecretReference(String secretReferenceId) {
        SecretReferenceRecord current = secretReferenceCatalogService.getRequired(secretReferenceId);
        if (!current.linkedDeploymentConnections().isEmpty() || !current.linkedReleaseSources().isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "secret reference is still in use; update linked deployment connections or release sources before deleting it."
            );
        }
        secretReferenceCommandRepository.deleteSecretReference(current.id());
    }
}
