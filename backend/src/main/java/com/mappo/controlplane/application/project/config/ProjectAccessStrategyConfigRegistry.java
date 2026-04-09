package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.project.ProjectAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.util.JsonUtil;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectAccessStrategyConfigRegistry
    extends AbstractProjectConfigDescriptorRegistry<ProjectAccessStrategyType, ProjectAccessStrategyConfig, ProjectAccessStrategyConfigDescriptor> {

    public ProjectAccessStrategyConfigRegistry(
        List<ProjectAccessStrategyConfigDescriptor> descriptors,
        JsonUtil jsonUtil,
        ObjectMapper objectMapper
    ) {
        super(descriptors, jsonUtil, objectMapper);
    }
}
