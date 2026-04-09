package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.model.RunPreviewMode;
import com.mappo.controlplane.util.JsonUtil;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectDeploymentDriverConfigRegistry
    extends AbstractProjectConfigDescriptorRegistry<ProjectDeploymentDriverType, ProjectDeploymentDriverConfig, ProjectDeploymentDriverConfigDescriptor> {

    public ProjectDeploymentDriverConfigRegistry(
        List<ProjectDeploymentDriverConfigDescriptor> descriptors,
        JsonUtil jsonUtil,
        ObjectMapper objectMapper
    ) {
        super(descriptors, jsonUtil, objectMapper);
    }

    public DeploymentDriverCapabilities capabilities(
        ProjectDeploymentDriverType type,
        ProjectDeploymentDriverConfig config,
        boolean hasDeploymentDriver,
        RunPreviewMode previewMode
    ) {
        ProjectDeploymentDriverConfigDescriptor descriptor = descriptor(type);
        return descriptor.capabilities(config, hasDeploymentDriver, previewMode);
    }
}
