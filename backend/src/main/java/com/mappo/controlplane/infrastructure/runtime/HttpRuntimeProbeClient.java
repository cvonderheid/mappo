package com.mappo.controlplane.infrastructure.runtime;

import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class HttpRuntimeProbeClient {

    public TargetRuntimeProbeRecord probe(String targetId, OffsetDateTime checkedAt, HttpRuntimeProbeRequest request) {
        TargetRuntimeProbeRecord primary = send(targetId, checkedAt, request.primaryUrl(), request.expectedStatus(), request.timeoutMs());
        if (completed(primary) || isBlank(request.fallbackUrl())) {
            return primary;
        }
        return send(targetId, checkedAt, request.fallbackUrl(), request.expectedStatus(), request.timeoutMs());
    }

    private TargetRuntimeProbeRecord send(
        String targetId,
        OffsetDateTime checkedAt,
        String url,
        int expectedStatus,
        long timeoutMs
    ) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "mappo-runtime-probe")
                .GET()
                .build();
            HttpResponse<Void> response = httpClient(timeoutMs).send(request, HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            boolean expected = statusCode == expectedStatus;
            return new TargetRuntimeProbeRecord(
                targetId,
                expected ? MappoRuntimeProbeStatus.healthy : MappoRuntimeProbeStatus.unhealthy,
                checkedAt,
                url,
                statusCode,
                expected
                    ? "Runtime responded with HTTP " + statusCode + "."
                    : "Runtime responded with HTTP " + statusCode + " (expected " + expectedStatus + ")."
            );
        } catch (HttpConnectTimeoutException error) {
            return failure(targetId, checkedAt, MappoRuntimeProbeStatus.unreachable, url, "Runtime probe timed out: " + summarizeError(error));
        } catch (IOException error) {
            return failure(targetId, checkedAt, MappoRuntimeProbeStatus.unreachable, url, "Runtime probe failed: " + summarizeError(error));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return failure(targetId, checkedAt, MappoRuntimeProbeStatus.unreachable, url, "Runtime probe interrupted.");
        } catch (IllegalArgumentException error) {
            return failure(targetId, checkedAt, MappoRuntimeProbeStatus.unknown, url, "Runtime probe URL is invalid: " + summarizeError(error));
        }
    }

    private boolean completed(TargetRuntimeProbeRecord record) {
        return record != null && record.runtimeStatus() != MappoRuntimeProbeStatus.unreachable && record.runtimeStatus() != MappoRuntimeProbeStatus.unknown;
    }

    private TargetRuntimeProbeRecord failure(
        String targetId,
        OffsetDateTime checkedAt,
        MappoRuntimeProbeStatus status,
        String url,
        String summary
    ) {
        return new TargetRuntimeProbeRecord(targetId, status, checkedAt, url, null, summary);
    }

    private HttpClient httpClient(long timeoutMs) {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    private String summarizeError(Throwable error) {
        String message = error == null ? "" : Objects.toString(error.getMessage(), "").trim();
        return message.isBlank()
            ? error == null ? "unknown error" : error.getClass().getSimpleName()
            : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }
}
