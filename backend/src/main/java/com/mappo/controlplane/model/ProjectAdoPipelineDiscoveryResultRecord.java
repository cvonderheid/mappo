package com.mappo.controlplane.model;

import java.util.List;

public record ProjectAdoPipelineDiscoveryResultRecord(
    String projectId,
    String organization,
    String project,
    List<ProjectAdoPipelineRecord> pipelines
) {
}
