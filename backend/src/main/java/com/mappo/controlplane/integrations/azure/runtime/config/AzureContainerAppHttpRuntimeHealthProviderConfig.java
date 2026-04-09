package com.mappo.controlplane.integrations.azure.runtime.config;

import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderConfig;

public record AzureContainerAppHttpRuntimeHealthProviderConfig(
    String path,
    int expectedStatus,
    long timeoutMs
) implements ProjectRuntimeHealthProviderConfig {

    public static AzureContainerAppHttpRuntimeHealthProviderConfig defaults() {
        return new AzureContainerAppHttpRuntimeHealthProviderConfig("/health", 200, 5_000L);
    }
}
