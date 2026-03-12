package com.mappo.controlplane.model;

import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import java.util.Map;
import java.util.UUID;

public record TargetRuntimeProbeContextRecord(
    String targetId,
    String projectId,
    UUID tenantId,
    UUID subscriptionId,
    String containerAppResourceId,
    ProjectRuntimeHealthProviderType runtimeHealthProvider,
    String runtimeHealthPath,
    int expectedStatus,
    long timeoutMs,
    Map<String, String> executionConfig
) {

    public TargetRuntimeProbeContextRecord {
        executionConfig = executionConfig == null ? Map.of() : Map.copyOf(executionConfig);
    }

    public String resolvedRuntimeBaseUrl() {
        return normalize(executionConfig.get("runtimeBaseUrl"));
    }

    public String resolvedRuntimeHealthPath() {
        String override = normalize(executionConfig.get("runtimeHealthPath"));
        return override.isBlank() ? normalize(runtimeHealthPath) : override;
    }

    public int resolvedExpectedStatus() {
        return parseInt(executionConfig.get("runtimeExpectedStatus"), expectedStatus);
    }

    public long resolvedTimeoutMs() {
        return parseLong(executionConfig.get("runtimeHealthTimeoutMs"), timeoutMs);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int parseInt(String value, int fallback) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
