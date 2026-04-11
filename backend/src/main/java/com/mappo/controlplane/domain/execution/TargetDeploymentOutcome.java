package com.mappo.controlplane.domain.execution;

import com.mappo.controlplane.model.ExternalExecutionHandleRecord;

public record TargetDeploymentOutcome(
    String correlationId,
    String message,
    String portalLink,
    ExternalExecutionHandleRecord externalExecutionHandle
) {
    public TargetDeploymentOutcome(
        String correlationId,
        String message,
        String portalLink
    ) {
        this(correlationId, message, portalLink, null);
    }
}
