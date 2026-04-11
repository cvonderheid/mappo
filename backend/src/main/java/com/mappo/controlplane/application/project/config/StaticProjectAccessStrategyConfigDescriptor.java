package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.project.ProjectAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;

public record StaticProjectAccessStrategyConfigDescriptor(
    ProjectAccessStrategyType key,
    Class<? extends ProjectAccessStrategyConfig> configType,
    ProjectAccessStrategyConfig defaults
) implements ProjectAccessStrategyConfigDescriptor {
}
