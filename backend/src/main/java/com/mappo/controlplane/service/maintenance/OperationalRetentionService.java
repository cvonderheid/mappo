package com.mappo.controlplane.service.maintenance;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.repository.OperationalRetentionRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationalRetentionService {

    private final OperationalRetentionRepository retentionRepository;
    private final MappoProperties properties;
    private final LiveUpdateService liveUpdateService;

    @Scheduled(
        initialDelayString = "${mappo.retention-interval-ms:86400000}",
        fixedDelayString = "${mappo.retention-interval-ms:86400000}"
    )
    public void scheduledRetentionCleanup() {
        if (!properties.isRetentionEnabled()) {
            return;
        }
        pruneExpiredData();
    }

    public RetentionCleanupSummary pruneExpiredData() {
        if (!properties.isRetentionEnabled()) {
            return new RetentionCleanupSummary(0, 0, 0, 0);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime runCutoff = now.minusDays(Math.max(1, properties.getRunRetentionDays()));
        OffsetDateTime auditCutoff = now.minusDays(Math.max(1, properties.getAuditRetentionDays()));

        RetentionCleanupSummary summary = new RetentionCleanupSummary(
            retentionRepository.deleteExpiredRuns(runCutoff),
            retentionRepository.deleteExpiredMarketplaceEvents(auditCutoff),
            retentionRepository.deleteExpiredForwarderLogs(auditCutoff),
            retentionRepository.deleteExpiredReleaseWebhookDeliveries(auditCutoff)
        );

        if (summary.hasRunChanges()) {
            liveUpdateService.emitRunsUpdated();
        }
        if (summary.hasAdminChanges()) {
            liveUpdateService.emitAdminUpdated();
        }

        if (summary.hasAnyChanges()) {
            log.info(
                "Operational retention cleanup deleted runs={}, marketplaceEvents={}, forwarderLogs={}, releaseWebhookDeliveries={}",
                summary.deletedRuns(),
                summary.deletedMarketplaceEvents(),
                summary.deletedForwarderLogs(),
                summary.deletedReleaseWebhookDeliveries()
            );
        }

        return summary;
    }
}
