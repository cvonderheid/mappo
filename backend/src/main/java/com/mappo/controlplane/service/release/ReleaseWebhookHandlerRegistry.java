package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.application.release.ReleaseWebhookHandler;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReleaseWebhookHandlerRegistry {

    private final Map<ReleaseIngestProviderType, ReleaseWebhookHandler> handlers;

    public ReleaseWebhookHandlerRegistry(List<ReleaseWebhookHandler> handlers) {
        Map<ReleaseIngestProviderType, ReleaseWebhookHandler> mapped = new EnumMap<>(ReleaseIngestProviderType.class);
        for (ReleaseWebhookHandler handler : handlers) {
            ReleaseIngestProviderType provider = handler.provider();
            if (mapped.putIfAbsent(provider, handler) != null) {
                throw new IllegalStateException("Duplicate release webhook handler registered for " + provider);
            }
        }
        this.handlers = Map.copyOf(mapped);
    }

    public ReleaseWebhookHandler getRequired(ReleaseIngestProviderType provider) {
        ReleaseWebhookHandler handler = handlers.get(provider);
        if (handler == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Release webhook provider is not supported: " + provider);
        }
        return handler;
    }
}
