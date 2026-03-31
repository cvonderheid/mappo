package com.mappo.controlplane.model;

public record ProjectAdoRepositoryRecord(
    String id,
    String name,
    String defaultBranch,
    String webUrl,
    String remoteUrl
) {
}
