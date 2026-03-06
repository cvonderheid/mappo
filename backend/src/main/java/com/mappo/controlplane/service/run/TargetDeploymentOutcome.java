package com.mappo.controlplane.service.run;

public record TargetDeploymentOutcome(
    String correlationId,
    String message,
    String portalLink
) {
}
