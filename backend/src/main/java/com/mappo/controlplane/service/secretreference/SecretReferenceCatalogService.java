package com.mappo.controlplane.service.secretreference;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.model.SecretReferenceRecord;
import com.mappo.controlplane.persistence.secretreference.SecretReferenceQueryRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretReferenceCatalogService {

    private final SecretReferenceQueryRepository secretReferenceQueryRepository;

    public List<SecretReferenceRecord> listSecretReferences() {
        return secretReferenceQueryRepository.listSecretReferences();
    }

    public Optional<SecretReferenceRecord> getSecretReference(String secretReferenceId) {
        return secretReferenceQueryRepository.getSecretReference(secretReferenceId);
    }

    public SecretReferenceRecord getRequired(String secretReferenceId) {
        return getSecretReference(secretReferenceId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "secret reference not found: " + normalize(secretReferenceId)));
    }

    public boolean exists(String secretReferenceId) {
        return secretReferenceQueryRepository.exists(secretReferenceId);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
