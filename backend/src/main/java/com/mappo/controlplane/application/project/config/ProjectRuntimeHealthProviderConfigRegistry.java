package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.util.JsonUtil;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectRuntimeHealthProviderConfigRegistry
    extends AbstractProjectConfigDescriptorRegistry<ProjectRuntimeHealthProviderType, ProjectRuntimeHealthProviderConfig, ProjectRuntimeHealthProviderConfigDescriptor> {

    public ProjectRuntimeHealthProviderConfigRegistry(
        List<ProjectRuntimeHealthProviderConfigDescriptor> descriptors,
        JsonUtil jsonUtil,
        ObjectMapper objectMapper
    ) {
        super(descriptors, jsonUtil, objectMapper);
    }
}
