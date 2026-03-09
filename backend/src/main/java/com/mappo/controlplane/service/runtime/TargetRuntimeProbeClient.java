package com.mappo.controlplane.service.runtime;

import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;

public interface TargetRuntimeProbeClient {

    boolean isConfigured();

    TargetRuntimeProbeRecord probe(TargetRuntimeProbeContextRecord target);
}
