package com.mappo.controlplane.persistence.target;

import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_RUNTIME_PROBES;
import static com.mappo.controlplane.jooq.Tables.TARGETS;

import com.mappo.controlplane.model.TargetRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetRecordQueryRepository {

    private final DSLContext dsl;

    public List<TargetRecord> listTargets(Map<String, String> filters) {
        var support = new TargetQuerySupport(dsl);
        var latestExecution = support.latestExecutionTable();
        Condition condition = buildFilterCondition(filters, support);

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
            .fetch();

        return mapTargets(rows, support);
    }

    public Optional<TargetRecord> getTarget(String targetId) {
        var support = new TargetQuerySupport(dsl);
        var latestExecution = support.latestExecutionTable();
        Record row = dsl.select(
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
            .where(TARGETS.ID.eq(targetId))
            .fetchOne();

        if (row == null) {
            return Optional.empty();
        }
        Map<String, String> tags = support.loadTags(List.of(targetId)).getOrDefault(targetId, Map.of());
        return Optional.of(support.toTargetRecord(row, tags));
    }

    public List<TargetRecord> getTargetsByIds(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        var support = new TargetQuerySupport(dsl);
        var latestExecution = support.latestExecutionTable();
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
            .where(TARGETS.ID.in(targetIds))
            .orderBy(TARGETS.ID.asc())
            .fetch();
        return mapTargets(rows, support);
    }

    public List<TargetRecord> getTargetsByTagFilters(Map<String, String> filters) {
        return listTargets(filters == null ? Map.of() : filters);
    }

    public List<TargetRecord> getTargetsByIdsForProject(List<String> targetIds, String projectId) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        var support = new TargetQuerySupport(dsl);
        var latestExecution = support.latestExecutionTable();
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
            .where(TARGETS.ID.in(targetIds))
            .and(TARGETS.PROJECT_ID.eq(projectId))
            .orderBy(TARGETS.ID.asc())
            .fetch();
        return mapTargets(rows, support);
    }

    public List<TargetRecord> getTargetsByTagFiltersForProject(Map<String, String> filters, String projectId) {
        var support = new TargetQuerySupport(dsl);
        var latestExecution = support.latestExecutionTable();
        Condition condition = buildFilterCondition(filters, support).and(TARGETS.PROJECT_ID.eq(projectId));

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
            .fetch();

        return mapTargets(rows, support);
    }

    private Condition buildFilterCondition(Map<String, String> filters, TargetQuerySupport support) {
        Condition condition = org.jooq.impl.DSL.trueCondition();
        if (filters == null) {
            return condition;
        }
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = support.normalize(entry.getKey());
            String value = support.normalize(entry.getValue());
            if (key.isBlank() || value.isBlank()) {
                continue;
            }
            condition = condition.and(support.tagEqualsCondition(key, value));
        }
        return condition;
    }

    private List<TargetRecord> mapTargets(List<? extends Record> rows, TargetQuerySupport support) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> targetIds = rows.stream().map(row -> row.get(TARGETS.ID)).toList();
        Map<String, Map<String, String>> tagsByTarget = support.loadTags(targetIds);
        List<TargetRecord> targets = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGETS.ID);
            targets.add(support.toTargetRecord(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }
        return targets;
    }
}
