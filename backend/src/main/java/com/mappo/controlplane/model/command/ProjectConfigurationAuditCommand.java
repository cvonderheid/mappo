package com.mappo.controlplane.model.command;

import com.mappo.controlplane.model.ProjectConfigurationAuditAction;
import java.time.OffsetDateTime;
import java.util.Map;

public record ProjectConfigurationAuditCommand(
    String id,
    String projectId,
    ProjectConfigurationAuditAction action,
    String actor,
    String changeSummary,
    Map<String, Object> beforeSnapshot,
    Map<String, Object> afterSnapshot,
    OffsetDateTime createdAt
) {
}

