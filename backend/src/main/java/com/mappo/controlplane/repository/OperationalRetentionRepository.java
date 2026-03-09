package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.FORWARDER_LOGS;
import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;
import static com.mappo.controlplane.jooq.Tables.RELEASE_WEBHOOK_DELIVERIES;
import static com.mappo.controlplane.jooq.Tables.RUNS;

import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OperationalRetentionRepository {

    private final DSLContext dsl;

    public int deleteExpiredRuns(OffsetDateTime cutoff) {
        return dsl.deleteFrom(RUNS)
            .where(RUNS.STATUS.in(
                MappoRunStatus.succeeded,
                MappoRunStatus.failed,
                MappoRunStatus.partial,
                MappoRunStatus.halted
            ))
            .and(RUNS.ENDED_AT.isNotNull())
            .and(RUNS.ENDED_AT.lt(cutoff))
            .execute();
    }

    public int deleteExpiredMarketplaceEvents(OffsetDateTime cutoff) {
        return dsl.deleteFrom(MARKETPLACE_EVENTS)
            .where(MARKETPLACE_EVENTS.CREATED_AT.lt(cutoff))
            .execute();
    }

    public int deleteExpiredForwarderLogs(OffsetDateTime cutoff) {
        return dsl.deleteFrom(FORWARDER_LOGS)
            .where(FORWARDER_LOGS.CREATED_AT.lt(cutoff))
            .execute();
    }

    public int deleteExpiredReleaseWebhookDeliveries(OffsetDateTime cutoff) {
        return dsl.deleteFrom(RELEASE_WEBHOOK_DELIVERIES)
            .where(RELEASE_WEBHOOK_DELIVERIES.RECEIVED_AT.lt(cutoff))
            .execute();
    }
}
