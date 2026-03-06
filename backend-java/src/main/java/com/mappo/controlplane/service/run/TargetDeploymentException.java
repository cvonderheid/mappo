package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.StageErrorRecord;
import lombok.Getter;

@Getter
public class TargetDeploymentException extends RuntimeException {

    private final StageErrorRecord error;
    private final String correlationId;
    private final String portalLink;

    public TargetDeploymentException(
        String message,
        StageErrorRecord error,
        String correlationId,
        String portalLink
    ) {
        super(message);
        this.error = error;
        this.correlationId = correlationId;
        this.portalLink = portalLink == null ? "" : portalLink;
    }
}
