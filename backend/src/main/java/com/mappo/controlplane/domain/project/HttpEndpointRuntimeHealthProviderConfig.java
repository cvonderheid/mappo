package com.mappo.controlplane.domain.project;

public record HttpEndpointRuntimeHealthProviderConfig(
    String path,
    int expectedStatus,
    long timeoutMs
) implements ProjectRuntimeHealthProviderConfig {

    public static HttpEndpointRuntimeHealthProviderConfig defaults() {
        return new HttpEndpointRuntimeHealthProviderConfig("/health", 200, 5_000L);
    }
}
