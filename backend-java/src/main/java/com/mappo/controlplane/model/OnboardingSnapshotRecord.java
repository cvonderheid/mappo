package com.mappo.controlplane.model;

import java.util.List;

public record OnboardingSnapshotRecord(
    List<TargetRegistrationRecord> registrations,
    List<MarketplaceEventRecord> events,
    List<ForwarderLogRecord> forwarderLogs
) {
}
