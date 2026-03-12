package com.mappo.controlplane.infrastructure.azure.runtime;

import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.Configuration;
import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.azure.resourcemanager.appcontainers.models.Ingress;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.health.RuntimeHealthProvider;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.infrastructure.runtime.HttpRuntimeProbeClient;
import com.mappo.controlplane.infrastructure.runtime.HttpRuntimeProbeRequest;
import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AzureContainerAppRuntimeHealthProvider implements RuntimeHealthProvider {

    private final AzureExecutorClient azureExecutorClient;
    private final HttpRuntimeProbeClient httpRuntimeProbeClient;
    private final MappoProperties properties;

    @Override
    public boolean supports(TargetRuntimeProbeContextRecord target) {
        return target != null
            && target.runtimeHealthProvider() == ProjectRuntimeHealthProviderType.azure_container_app_http
            && !normalize(target.containerAppResourceId()).isBlank()
            && target.tenantId() != null
            && target.subscriptionId() != null;
    }

    @Override
    public boolean isConfigured() {
        return !normalize(properties.getAzure().getTenantId()).isBlank()
            && !normalize(properties.getAzure().getClientId()).isBlank()
            && !normalize(properties.getAzure().getClientSecret()).isBlank();
    }

    @Override
    public TargetRuntimeProbeRecord probe(TargetRuntimeProbeContextRecord target) {
        OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            ContainerAppsApiManager manager = azureExecutorClient.createContainerAppsManager(
                uuidText(target.tenantId()),
                uuidText(target.subscriptionId())
            );
            ContainerApp containerApp = manager.containerApps().getById(normalize(target.containerAppResourceId()));
            if (containerApp == null) {
                return result(target.targetId(), MappoRuntimeProbeStatus.unknown, checkedAt, null, null, "Container App metadata was not found.");
            }
            return probeContainerApp(target, containerApp, checkedAt);
        } catch (RuntimeException error) {
            return result(
                target.targetId(),
                MappoRuntimeProbeStatus.unknown,
                checkedAt,
                null,
                null,
                "Azure metadata unavailable: " + summarizeError(error)
            );
        }
    }

    private TargetRuntimeProbeRecord probeContainerApp(
        TargetRuntimeProbeContextRecord target,
        ContainerApp containerApp,
        OffsetDateTime checkedAt
    ) {
        Configuration configuration = containerApp.configuration();
        Ingress ingress = configuration == null ? null : configuration.ingress();
        String fqdn = firstNonBlank(
            ingress == null ? null : ingress.fqdn(),
            containerApp.latestRevisionFqdn()
        );
        if (fqdn.isBlank()) {
            return result(target.targetId(), MappoRuntimeProbeStatus.unknown, checkedAt, null, null, "Container App has no public ingress FQDN.");
        }
        if (ingress != null && Boolean.FALSE.equals(ingress.external())) {
            return result(
                target.targetId(),
                MappoRuntimeProbeStatus.unknown,
                checkedAt,
                buildUrl("https", fqdn),
                null,
                "Container App ingress is internal-only."
            );
        }

        boolean allowInsecure = ingress != null && Boolean.TRUE.equals(ingress.allowInsecure());
        long timeoutMs = target.resolvedTimeoutMs() > 0 ? target.resolvedTimeoutMs() : properties.getRuntimeProbe().getTimeoutMs();
        return httpRuntimeProbeClient.probe(
            target.targetId(),
            checkedAt,
            new HttpRuntimeProbeRequest(
                buildUrl("https", fqdn),
                allowInsecure ? buildUrl("http", fqdn) : null,
                200,
                timeoutMs
            )
        );
    }

    private TargetRuntimeProbeRecord result(
        String targetId,
        MappoRuntimeProbeStatus status,
        OffsetDateTime checkedAt,
        String endpointUrl,
        Integer httpStatusCode,
        String summary
    ) {
        return new TargetRuntimeProbeRecord(
            targetId,
            status,
            checkedAt,
            endpointUrl,
            httpStatusCode,
            normalize(summary)
        );
    }

    private String buildUrl(String scheme, String fqdn) {
        return scheme + "://" + normalize(fqdn);
    }

    private String uuidText(Object value) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException("tenant/subscription identifier is required");
        }
        return text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String summarizeError(Throwable error) {
        String message = normalize(error == null ? null : error.getMessage());
        if (!message.isBlank()) {
            return message;
        }
        return error == null ? "unknown error" : error.getClass().getSimpleName();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
