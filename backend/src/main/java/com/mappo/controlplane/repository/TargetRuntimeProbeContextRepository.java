package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;

import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetRuntimeProbeContextRepository {

    private final DSLContext dsl;

    public List<TargetRuntimeProbeContextRecord> listRuntimeProbeContexts() {
        return dsl.select(
                TARGETS.ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID
            )
            .from(TARGETS)
            .join(TARGET_REGISTRATIONS)
            .on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
            .orderBy(TARGETS.ID.asc())
            .fetch(row -> new TargetRuntimeProbeContextRecord(
                row.get(TARGETS.ID),
                row.get(TARGETS.TENANT_ID),
                row.get(TARGETS.SUBSCRIPTION_ID),
                row.get(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID)
            ));
    }
}
