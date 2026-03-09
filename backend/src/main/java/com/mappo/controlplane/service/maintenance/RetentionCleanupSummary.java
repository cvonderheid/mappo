package com.mappo.controlplane.service.maintenance;

public record RetentionCleanupSummary(
    int deletedRuns,
    int deletedMarketplaceEvents,
    int deletedForwarderLogs,
    int deletedReleaseWebhookDeliveries
) {

    public boolean hasRunChanges() {
        return deletedRuns > 0;
    }

    public boolean hasAdminChanges() {
        return deletedMarketplaceEvents > 0
            || deletedForwarderLogs > 0
            || deletedReleaseWebhookDeliveries > 0;
    }

    public boolean hasAnyChanges() {
        return hasRunChanges() || hasAdminChanges();
    }
}
