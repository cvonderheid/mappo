package com.mappo.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import com.mappo.controlplane.service.maintenance.OperationalRetentionService;
import com.mappo.controlplane.service.maintenance.RetentionCleanupSummary;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "mappo.retention.enabled=true",
    "mappo.retention.run-retention-days=30",
    "mappo.retention.audit-retention-days=30",
    "mappo.runtime-probe.enabled=false"
})
class OperationalRetentionIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OperationalRetentionService operationalRetentionService;

    @Test
    void pruneExpiredDataDeletesOldTerminalAndAuditRecordsOnly() {
        OffsetDateTime oldTimestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(45);
        OffsetDateTime freshTimestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5);

        insertRun("run-old-terminal", "succeeded", oldTimestamp, oldTimestamp, oldTimestamp);
        insertRun("run-old-running", "running", oldTimestamp, oldTimestamp, null);
        insertRun("run-fresh-terminal", "failed", freshTimestamp, freshTimestamp, freshTimestamp);

        jdbcTemplate.update(
            """
            INSERT INTO marketplace_events (
              id, event_type, status, message, tenant_id, subscription_id, created_at, processed_at
            ) VALUES (?, 'subscription_purchased', 'applied', ?, ?, ?, ?, ?)
            """,
            "evt-old",
            "old event",
            java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
            java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"),
            timestamp(oldTimestamp),
            timestamp(oldTimestamp)
        );
        jdbcTemplate.update(
            """
            INSERT INTO marketplace_events (
              id, event_type, status, message, tenant_id, subscription_id, created_at, processed_at
            ) VALUES (?, 'subscription_purchased', 'applied', ?, ?, ?, ?, ?)
            """,
            "evt-fresh",
            "fresh event",
            java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"),
            java.util.UUID.fromString("44444444-4444-4444-4444-444444444444"),
            timestamp(freshTimestamp),
            timestamp(freshTimestamp)
        );

        jdbcTemplate.update(
            """
            INSERT INTO forwarder_logs (
              id, level, message, created_at
            ) VALUES (?, 'info', ?, ?)
            """,
            "log-old",
            "old log",
            timestamp(oldTimestamp)
        );
        jdbcTemplate.update(
            """
            INSERT INTO forwarder_logs (
              id, level, message, created_at
            ) VALUES (?, 'warning', ?, ?)
            """,
            "log-fresh",
            "fresh log",
            timestamp(freshTimestamp)
        );

        jdbcTemplate.update(
            """
            INSERT INTO release_webhook_deliveries (
              id, external_delivery_id, event_type, repo, ref, manifest_path, status, message, received_at
            ) VALUES (?, ?, 'push', 'cvonderheid/mappo-managed-app', 'main', 'releases/releases.manifest.json', 'applied', ?, ?)
            """,
            "delivery-old",
            "delivery-old",
            "old delivery",
            timestamp(oldTimestamp)
        );
        jdbcTemplate.update(
            """
            INSERT INTO release_webhook_deliveries (
              id, external_delivery_id, event_type, repo, ref, manifest_path, status, message, received_at
            ) VALUES (?, ?, 'push', 'cvonderheid/mappo-managed-app', 'main', 'releases/releases.manifest.json', 'skipped', ?, ?)
            """,
            "delivery-fresh",
            "delivery-fresh",
            "fresh delivery",
            timestamp(freshTimestamp)
        );

        RetentionCleanupSummary summary = operationalRetentionService.pruneExpiredData();

        assertThat(summary.deletedRuns()).isEqualTo(1);
        assertThat(summary.deletedMarketplaceEvents()).isEqualTo(1);
        assertThat(summary.deletedForwarderLogs()).isEqualTo(1);
        assertThat(summary.deletedReleaseWebhookDeliveries()).isEqualTo(1);

        assertThat(count("SELECT COUNT(*) FROM runs WHERE id = 'run-old-terminal'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM runs WHERE id = 'run-old-running'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM runs WHERE id = 'run-fresh-terminal'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM marketplace_events WHERE id = 'evt-old'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM marketplace_events WHERE id = 'evt-fresh'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM forwarder_logs WHERE id = 'log-old'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM forwarder_logs WHERE id = 'log-fresh'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM release_webhook_deliveries WHERE id = 'delivery-old'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM release_webhook_deliveries WHERE id = 'delivery-fresh'")).isEqualTo(1);
    }

    private void insertRun(
        String runId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime endedAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO runs (
              id,
              release_id,
              execution_source_type,
              strategy_mode,
              wave_tag,
              concurrency,
              subscription_concurrency,
              status,
              halt_reason,
              created_at,
              started_at,
              ended_at,
              updated_at
            ) VALUES (?, ?, 'deployment_stack', 'all_at_once', 'ring', 1, 1, ?::mappo_run_status, NULL, ?, ?, ?, ?)
            """,
            runId,
            "rel-retention",
            status,
            timestamp(createdAt),
            timestamp(createdAt),
            endedAt == null ? null : timestamp(endedAt),
            timestamp(updatedAt)
        );
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return Timestamp.from(value.toInstant());
    }
}
