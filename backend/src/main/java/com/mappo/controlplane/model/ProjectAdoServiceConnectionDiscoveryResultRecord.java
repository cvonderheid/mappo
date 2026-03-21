package com.mappo.controlplane.model;

import java.util.List;

public record ProjectAdoServiceConnectionDiscoveryResultRecord(
    String projectId,
    String organization,
    String project,
    List<ProjectAdoServiceConnectionRecord> serviceConnections
) {
}
