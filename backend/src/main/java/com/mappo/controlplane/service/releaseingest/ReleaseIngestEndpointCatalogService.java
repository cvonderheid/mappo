package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.repository.ReleaseIngestEndpointQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseIngestEndpointCatalogService {

    private final ReleaseIngestEndpointQueryRepository releaseIngestEndpointQueryRepository;

    public List<ReleaseIngestEndpointRecord> listEndpoints() {
        return releaseIngestEndpointQueryRepository.listEndpoints();
    }

    public ReleaseIngestEndpointRecord getRequired(String endpointId) {
        return releaseIngestEndpointQueryRepository.getEndpoint(endpointId).orElseThrow(
            () -> new ApiException(HttpStatus.BAD_REQUEST, "release source not found: " + normalize(endpointId))
        );
    }

    public boolean exists(String endpointId) {
        return releaseIngestEndpointQueryRepository.exists(endpointId);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
