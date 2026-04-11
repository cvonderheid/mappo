package com.mappo.controlplane.service.releaseingest;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.application.releaseingest.ReleaseIngestProviderDescriptor;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReleaseIngestProviderDescriptorRegistry {

    private final Map<ReleaseIngestProviderType, ReleaseIngestProviderDescriptor> descriptors;

    public ReleaseIngestProviderDescriptorRegistry(List<ReleaseIngestProviderDescriptor> descriptors) {
        Map<ReleaseIngestProviderType, ReleaseIngestProviderDescriptor> mapped = new EnumMap<>(ReleaseIngestProviderType.class);
        for (ReleaseIngestProviderDescriptor descriptor : descriptors) {
            ReleaseIngestProviderType provider = descriptor.provider();
            if (mapped.putIfAbsent(provider, descriptor) != null) {
                throw new IllegalStateException("Duplicate release ingest provider descriptor registered for " + provider);
            }
        }
        this.descriptors = Map.copyOf(mapped);
    }

    public ReleaseIngestProviderDescriptor getRequired(ReleaseIngestProviderType provider) {
        ReleaseIngestProviderDescriptor descriptor = descriptors.get(provider);
        if (descriptor == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported release source provider: " + provider);
        }
        return descriptor;
    }
}
