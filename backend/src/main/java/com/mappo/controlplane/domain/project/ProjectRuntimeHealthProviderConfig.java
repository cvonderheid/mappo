package com.mappo.controlplane.domain.project;

public sealed interface ProjectRuntimeHealthProviderConfig
    permits AzureContainerAppHttpRuntimeHealthProviderConfig, HttpEndpointRuntimeHealthProviderConfig {
}
