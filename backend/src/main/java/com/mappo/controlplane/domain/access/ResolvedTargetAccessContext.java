package com.mappo.controlplane.domain.access;

public sealed interface ResolvedTargetAccessContext
    permits SimulatorTargetAccessContext, AzureWorkloadRbacTargetAccessContext, LighthouseDelegatedTargetAccessContext {
}
