package com.mappo.controlplane.domain.execution;

import com.mappo.controlplane.model.ExternalExecutionHandleRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import lombok.Getter;

@Getter
public class TargetDeploymentException extends RuntimeException {

    private final StageErrorRecord error;
    private final String correlationId;
    private final String portalLink;
    private final ExternalExecutionHandleRecord externalExecutionHandle;

    public TargetDeploymentException(
        String message,
        StageErrorRecord error,
        String correlationId,
        String portalLink
    ) {
        this(message, error, correlationId, portalLink, null);
    }

    public TargetDeploymentException(
        String message,
        StageErrorRecord error,
        String correlationId,
        String portalLink,
        ExternalExecutionHandleRecord externalExecutionHandle
    ) {
        super(message);
        this.error = error;
        this.correlationId = correlationId;
        this.portalLink = portalLink == null ? "" : portalLink;
        this.externalExecutionHandle = externalExecutionHandle;
    }
}
