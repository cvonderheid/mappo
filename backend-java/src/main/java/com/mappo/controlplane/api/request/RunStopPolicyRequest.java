package com.mappo.controlplane.api.request;

import com.mappo.controlplane.model.command.RunStopPolicyCommand;

public record RunStopPolicyRequest(
    Integer maxFailureCount,
    Double maxFailureRate
) {
    public RunStopPolicyCommand toCommand() {
        return new RunStopPolicyCommand(maxFailureCount, maxFailureRate);
    }
}
