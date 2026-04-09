package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.persistence.providerconnection.ProviderConnectionQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderConnectionCatalogService {

    private final ProviderConnectionQueryRepository providerConnectionQueryRepository;

    public List<ProviderConnectionRecord> listConnections() {
        return providerConnectionQueryRepository.listConnections();
    }

    public ProviderConnectionRecord getRequired(String connectionId) {
        return providerConnectionQueryRepository.getConnection(connectionId).orElseThrow(
            () -> new ApiException(HttpStatus.BAD_REQUEST, "deployment connection not found: " + normalize(connectionId))
        );
    }

    public boolean exists(String connectionId) {
        return providerConnectionQueryRepository.exists(connectionId);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
