package com.mappo.controlplane.service.providerconnection;

import com.mappo.controlplane.application.providerconnection.ProviderConnectionProviderDescriptor;
import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderConnectionProviderDescriptorRegistry {

    private final List<ProviderConnectionProviderDescriptor> descriptors;

    public Optional<ProviderConnectionProviderDescriptor> find(ProviderConnectionProviderType provider) {
        if (provider == null) {
            return Optional.empty();
        }
        return descriptors.stream()
            .filter(descriptor -> descriptor.provider() == provider)
            .findFirst();
    }

    public ProviderConnectionProviderDescriptor getRequired(ProviderConnectionProviderType provider) {
        return find(provider)
            .orElseThrow(() -> new IllegalArgumentException("unsupported deployment connection provider: " + provider));
    }
}
