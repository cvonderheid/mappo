package com.mappo.controlplane.model.query;

import com.mappo.controlplane.model.ProjectConfigurationAuditAction;

public record ProjectConfigurationAuditPageQuery(
    Integer page,
    Integer size,
    String projectId,
    ProjectConfigurationAuditAction action
) {
}

