package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.util.JsonUtil;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectReleaseArtifactSourceConfigRegistry
    extends AbstractProjectConfigDescriptorRegistry<ProjectReleaseArtifactSourceType, ProjectReleaseArtifactSourceConfig, ProjectReleaseArtifactSourceConfigDescriptor> {

    public ProjectReleaseArtifactSourceConfigRegistry(
        List<ProjectReleaseArtifactSourceConfigDescriptor> descriptors,
        JsonUtil jsonUtil,
        ObjectMapper objectMapper
    ) {
        super(descriptors, jsonUtil, objectMapper);
    }
}
