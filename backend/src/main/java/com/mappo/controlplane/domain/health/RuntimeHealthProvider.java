package com.mappo.controlplane.domain.health;

import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;

public interface RuntimeHealthProvider {

    boolean supports(TargetRuntimeProbeContextRecord target);

    boolean isConfigured();

    TargetRuntimeProbeRecord probe(TargetRuntimeProbeContextRecord target);
}
