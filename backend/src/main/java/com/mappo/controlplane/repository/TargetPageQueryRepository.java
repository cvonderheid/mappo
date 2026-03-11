package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_RUNTIME_PROBES;
import static com.mappo.controlplane.jooq.Tables.TARGETS;

import com.mappo.controlplane.model.PageMetadataRecord;
import com.mappo.controlplane.model.TargetPageRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.query.TargetPageQuery;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetPageQueryRepository {

    private final DSLContext dsl;

    public TargetPageRecord listTargetsPage(TargetPageQuery query) {
        var support = new TargetQuerySupport(dsl);
        int page = support.normalizePage(query == null ? null : query.page());
        int size = support.normalizeSize(query == null ? null : query.size());
        var latestExecution = support.latestExecutionTable();
        Condition condition = support.buildTargetPageCondition(query, latestExecution);
        String projectIdFilter = query == null ? "" : support.normalize(query.projectId());
        if (!projectIdFilter.isBlank()) {
            condition = condition.and(TARGETS.PROJECT_ID.eq(projectIdFilter));
        }

        long totalItems = dsl.fetchCount(
            dsl.select(TARGETS.ID)
                .from(TARGETS)
                .leftJoin(TARGET_REGISTRATIONS).on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
                .leftJoin(TARGET_RUNTIME_PROBES).on(TARGET_RUNTIME_PROBES.TARGET_ID.eq(TARGETS.ID))
                .leftJoin(latestExecution).on(latestExecution.field("target_id", String.class).eq(TARGETS.ID))
                .where(condition)
        );
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

        var rows = dsl.select(
                TARGETS.ID,
                TARGETS.PROJECT_ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGET_RUNTIME_PROBES.RUNTIME_STATUS,
                TARGET_RUNTIME_PROBES.CHECKED_AT,
                TARGET_RUNTIME_PROBES.SUMMARY,
                latestExecution.field("latest_status", com.mappo.controlplane.jooq.enums.MappoTargetStage.class),
                latestExecution.field("latest_updated_at", OffsetDateTime.class),
                TARGETS.LAST_CHECK_IN_AT,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .leftJoin(TARGET_REGISTRATIONS).on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
            .leftJoin(TARGET_RUNTIME_PROBES).on(TARGET_RUNTIME_PROBES.TARGET_ID.eq(TARGETS.ID))
            .leftJoin(latestExecution).on(latestExecution.field("target_id", String.class).eq(TARGETS.ID))
            .where(condition)
            .orderBy(TARGETS.ID.asc())
            .limit(size)
            .offset(page * size)
            .fetch();

        if (rows.isEmpty()) {
            return new TargetPageRecord(List.of(), new PageMetadataRecord(page, size, totalItems, totalPages));
        }

        List<String> targetIds = rows.stream().map(row -> row.get(TARGETS.ID)).toList();
        Map<String, Map<String, String>> tagsByTarget = support.loadTags(targetIds);
        List<TargetRecord> targets = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGETS.ID);
            targets.add(support.toTargetRecord(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }

        return new TargetPageRecord(targets, new PageMetadataRecord(page, size, totalItems, totalPages));
    }
}
