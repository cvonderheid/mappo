package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;

public record StaticProjectRuntimeHealthProviderConfigDescriptor(
    ProjectRuntimeHealthProviderType key,
    Class<? extends ProjectRuntimeHealthProviderConfig> configType,
    ProjectRuntimeHealthProviderConfig defaults
) implements ProjectRuntimeHealthProviderConfigDescriptor {
}
