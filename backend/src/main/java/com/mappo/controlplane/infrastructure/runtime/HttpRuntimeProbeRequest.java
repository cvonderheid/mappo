package com.mappo.controlplane.infrastructure.runtime;

public record HttpRuntimeProbeRequest(
    String primaryUrl,
    String fallbackUrl,
    int expectedStatus,
    long timeoutMs
) {
}
