package com.mappo.controlplane.model;

import java.util.List;

public record ProjectAdoBranchDiscoveryResultRecord(
    String projectId,
    String organization,
    String project,
    String repositoryId,
    String repository,
    List<ProjectAdoBranchRecord> branches
) {
}
