package com.mappo.controlplane.model;

import java.util.List;

public record ProjectAdoRepositoryDiscoveryResultRecord(
    String projectId,
    String organization,
    String project,
    List<ProjectAdoRepositoryRecord> repositories
) {
}
