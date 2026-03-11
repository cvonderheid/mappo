package com.mappo.controlplane.infrastructure.azure.runtime;

import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.azure.resourcemanager.appcontainers.models.Configuration;
import com.azure.resourcemanager.appcontainers.models.Ingress;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.health.RuntimeHealthProvider;
import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AzureContainerAppRuntimeHealthProvider implements RuntimeHealthProvider {

    private final AzureExecutorClient azureExecutorClient;
    private final MappoProperties properties;

    @Override
    public boolean supports(TargetRuntimeProbeContextRecord target) {
        return target != null
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
            return probeContainerApp(target.targetId(), containerApp, checkedAt);
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
        String targetId,
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
            return result(targetId, MappoRuntimeProbeStatus.unknown, checkedAt, null, null, "Container App has no public ingress FQDN.");
        }
        if (ingress != null && Boolean.FALSE.equals(ingress.external())) {
            return result(
                targetId,
                MappoRuntimeProbeStatus.unknown,
                checkedAt,
                httpsUrl(fqdn),
                null,
                "Container App ingress is internal-only."
            );
        }

        String httpsUrl = httpsUrl(fqdn);
        boolean allowInsecure = ingress != null && Boolean.TRUE.equals(ingress.allowInsecure());
        return probeHttp(targetId, checkedAt, httpsUrl, allowInsecure ? httpUrl(fqdn) : null);
    }

    private TargetRuntimeProbeRecord probeHttp(
        String targetId,
        OffsetDateTime checkedAt,
        String primaryUrl,
        String fallbackUrl
    ) {
        HttpResult primary = send(primaryUrl);
        if (primary.completed()) {
            return primary.toRecord(targetId, checkedAt);
        }
        if (fallbackUrl != null && !fallbackUrl.isBlank()) {
            HttpResult fallback = send(fallbackUrl);
            if (fallback.completed()) {
                return fallback.toRecord(targetId, checkedAt);
            }
            return fallback.toRecord(targetId, checkedAt);
        }
        return primary.toRecord(targetId, checkedAt);
    }

    private HttpResult send(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(properties.getRuntimeProbe().getTimeoutMs()))
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "mappo-runtime-probe")
                .GET()
                .build();
            HttpResponse<Void> response = httpClient().send(request, HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            if (statusCode >= 500) {
                return new HttpResult(
                    MappoRuntimeProbeStatus.unhealthy,
                    url,
                    statusCode,
                    "Runtime responded with HTTP " + statusCode + ".",
                    true
                );
            }
            return new HttpResult(
                MappoRuntimeProbeStatus.healthy,
                url,
                statusCode,
                "Runtime responded with HTTP " + statusCode + ".",
                true
            );
        } catch (HttpConnectTimeoutException error) {
            return new HttpResult(
                MappoRuntimeProbeStatus.unreachable,
                url,
                null,
                "Runtime probe timed out: " + summarizeError(error),
                false
            );
        } catch (IOException error) {
            return new HttpResult(
                MappoRuntimeProbeStatus.unreachable,
                url,
                null,
                "Runtime probe failed: " + summarizeError(error),
                false
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new HttpResult(
                MappoRuntimeProbeStatus.unreachable,
                url,
                null,
                "Runtime probe interrupted.",
                false
            );
        } catch (IllegalArgumentException error) {
            return new HttpResult(
                MappoRuntimeProbeStatus.unknown,
                url,
                null,
                "Runtime probe URL is invalid: " + summarizeError(error),
                false
            );
        }
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getRuntimeProbe().getTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
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

    private String httpsUrl(String fqdn) {
        return "https://" + normalize(fqdn);
    }

    private String httpUrl(String fqdn) {
        return "http://" + normalize(fqdn);
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

    private record HttpResult(
        MappoRuntimeProbeStatus status,
        String url,
        Integer httpStatusCode,
        String summary,
        boolean completed
    ) {
        TargetRuntimeProbeRecord toRecord(String targetId, OffsetDateTime checkedAt) {
            return new TargetRuntimeProbeRecord(
                targetId,
                status,
                checkedAt,
                url,
                httpStatusCode,
                summary
            );
        }
    }
}
