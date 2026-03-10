package com.mappo.controlplane.service.admin;

import com.mappo.controlplane.model.command.TargetRegistrationUpsertCommand;
import com.mappo.controlplane.model.command.TargetUpsertCommand;

public record MarketplaceOnboardingTargetPlan(
    String targetId,
    TargetUpsertCommand targetCommand,
    TargetRegistrationUpsertCommand registrationCommand
) {
}
