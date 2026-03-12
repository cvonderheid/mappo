package com.mappo.controlplane.infrastructure.runtime;

import com.mappo.controlplane.domain.health.RuntimeHealthProvider;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HttpEndpointRuntimeHealthProvider implements RuntimeHealthProvider {

    private final HttpRuntimeProbeClient httpRuntimeProbeClient;

    @Override
    public boolean supports(TargetRuntimeProbeContextRecord target) {
        return target != null
            && target.runtimeHealthProvider() == ProjectRuntimeHealthProviderType.http_endpoint
            && !normalize(target.resolvedRuntimeBaseUrl()).isBlank();
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public TargetRuntimeProbeRecord probe(TargetRuntimeProbeContextRecord target) {
        OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return httpRuntimeProbeClient.probe(
            target.targetId(),
            checkedAt,
            new HttpRuntimeProbeRequest(
                buildUrl(target.resolvedRuntimeBaseUrl(), target.resolvedRuntimeHealthPath()),
                null,
                target.resolvedExpectedStatus(),
                target.resolvedTimeoutMs()
            )
        );
    }

    private String buildUrl(String baseUrl, String path) {
        String normalizedBaseUrl = normalize(baseUrl);
        String normalizedPath = normalize(path);
        if (normalizedBaseUrl.isBlank()) {
            throw new IllegalArgumentException("runtimeBaseUrl is required");
        }
        if (normalizedPath.isBlank()) {
            return normalizedBaseUrl;
        }
        return URI.create(normalizedBaseUrl).resolve(normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath).toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
