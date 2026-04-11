package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.project.HttpEndpointRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.domain.project.SimulatorAccessStrategyConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectConfigDescriptorConfiguration {

    @Bean
    ProjectAccessStrategyConfigDescriptor simulatorAccessStrategyConfigDescriptor() {
        return new StaticProjectAccessStrategyConfigDescriptor(
            ProjectAccessStrategyType.simulator,
            SimulatorAccessStrategyConfig.class,
            SimulatorAccessStrategyConfig.defaults()
        );
    }

    @Bean
    ProjectRuntimeHealthProviderConfigDescriptor httpEndpointRuntimeHealthProviderConfigDescriptor() {
        return new StaticProjectRuntimeHealthProviderConfigDescriptor(
            ProjectRuntimeHealthProviderType.http_endpoint,
            HttpEndpointRuntimeHealthProviderConfig.class,
            HttpEndpointRuntimeHealthProviderConfig.defaults()
        );
    }
}
