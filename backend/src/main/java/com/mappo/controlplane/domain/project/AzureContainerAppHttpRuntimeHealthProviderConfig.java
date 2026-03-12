package com.mappo.controlplane.domain.project;

public record AzureContainerAppHttpRuntimeHealthProviderConfig(
    String path,
    int expectedStatus,
    long timeoutMs
) implements ProjectRuntimeHealthProviderConfig {

    public static AzureContainerAppHttpRuntimeHealthProviderConfig defaults() {
        return new AzureContainerAppHttpRuntimeHealthProviderConfig("/health", 200, 5_000L);
    }
}
