package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.application.release.ReleaseManifestSourceClient;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReleaseManifestSourceClientRegistry {

    private final Map<ReleaseIngestProviderType, ReleaseManifestSourceClient> clients;

    public ReleaseManifestSourceClientRegistry(ConfigurableListableBeanFactory beanFactory) {
        Map<ReleaseIngestProviderType, ReleaseManifestSourceClient> mapped = new EnumMap<>(ReleaseIngestProviderType.class);
        Map<ReleaseIngestProviderType, String> beanNamesByProvider = new EnumMap<>(ReleaseIngestProviderType.class);
        Map<String, ReleaseManifestSourceClient> beans = new LinkedHashMap<>(beanFactory.getBeansOfType(ReleaseManifestSourceClient.class));
        for (Map.Entry<String, ReleaseManifestSourceClient> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            ReleaseManifestSourceClient client = entry.getValue();
            ReleaseIngestProviderType provider = client.provider();
            ReleaseManifestSourceClient existing = mapped.get(provider);
            if (existing == null) {
                mapped.put(provider, client);
                beanNamesByProvider.put(provider, beanName);
                continue;
            }
            String existingBeanName = beanNamesByProvider.get(provider);
            boolean currentPrimary = beanFactory.getBeanDefinition(beanName).isPrimary();
            boolean existingPrimary = beanFactory.getBeanDefinition(existingBeanName).isPrimary();
            if (currentPrimary && !existingPrimary) {
                mapped.put(provider, client);
                beanNamesByProvider.put(provider, beanName);
                continue;
            }
            if (!currentPrimary && existingPrimary) {
                continue;
            }
            if (currentPrimary) {
                throw new IllegalStateException("Duplicate primary release manifest source clients registered for " + provider);
            }
            throw new IllegalStateException("Duplicate release manifest source client registered for " + provider);
        }
        this.clients = Map.copyOf(mapped);
    }

    public ReleaseManifestSourceClient getRequired(ReleaseIngestProviderType provider) {
        ReleaseManifestSourceClient client = clients.get(provider);
        if (client == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Release manifest source provider is not supported: " + provider);
        }
        return client;
    }
}
