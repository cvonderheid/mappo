package com.mappo.controlplane.persistence.target;

import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_RECORDS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_RUNTIME_PROBES;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;
import static com.mappo.controlplane.jooq.Tables.TARGETS;

import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.query.TargetPageQuery;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

@RequiredArgsConstructor
final class TargetQuerySupport {

    private final DSLContext dsl;

    Table<?> latestExecutionTable() {
        var latestExecutionTimestamp = dsl.select(
                TARGET_EXECUTION_RECORDS.TARGET_ID.as("target_id"),
                DSL.max(TARGET_EXECUTION_RECORDS.UPDATED_AT).as("latest_updated_at")
            )
            .from(TARGET_EXECUTION_RECORDS)
            .groupBy(TARGET_EXECUTION_RECORDS.TARGET_ID)
            .asTable("latest_target_execution_ts");
        return dsl.select(
                TARGET_EXECUTION_RECORDS.TARGET_ID.as("target_id"),
                TARGET_EXECUTION_RECORDS.STATUS.as("latest_status"),
                TARGET_EXECUTION_RECORDS.UPDATED_AT.as("latest_updated_at")
            )
            .from(TARGET_EXECUTION_RECORDS)
            .join(latestExecutionTimestamp)
            .on(TARGET_EXECUTION_RECORDS.TARGET_ID.eq(latestExecutionTimestamp.field("target_id", String.class)))
            .and(TARGET_EXECUTION_RECORDS.UPDATED_AT.eq(latestExecutionTimestamp.field("latest_updated_at", OffsetDateTime.class)))
            .asTable("latest_target_execution");
    }

    Map<String, Map<String, String>> loadTags(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        var rows = dsl.select(TARGET_TAGS.TARGET_ID, TARGET_TAGS.TAG_KEY, TARGET_TAGS.TAG_VALUE)
            .from(TARGET_TAGS)
            .where(TARGET_TAGS.TARGET_ID.in(targetIds))
            .fetch();

        Map<String, Map<String, String>> tags = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get(TARGET_TAGS.TARGET_ID);
            tags.computeIfAbsent(targetId, key -> new LinkedHashMap<>())
                .put(row.get(TARGET_TAGS.TAG_KEY), row.get(TARGET_TAGS.TAG_VALUE));
        }
        return tags;
    }

    TargetRecord toTargetRecord(Record row, Map<String, String> tags) {
        return new TargetRecord(
            row.get(TARGETS.ID),
            row.get(TARGETS.PROJECT_ID),
            row.get(TARGETS.TENANT_ID),
            row.get(TARGETS.SUBSCRIPTION_ID),
            row.get(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID),
            row.get(TARGET_REGISTRATIONS.CUSTOMER_NAME),
            tags,
            row.get(TARGETS.LAST_DEPLOYED_RELEASE),
            row.get(TARGETS.HEALTH_STATUS),
            row.get(TARGET_RUNTIME_PROBES.RUNTIME_STATUS),
            row.get(TARGET_RUNTIME_PROBES.CHECKED_AT),
            nullableText(row.get(TARGET_RUNTIME_PROBES.SUMMARY)),
            row.get("latest_status", MappoTargetStage.class),
            row.get("latest_updated_at", OffsetDateTime.class),
            row.get(TARGETS.LAST_CHECK_IN_AT),
            row.get(TARGETS.SIMULATED_FAILURE_MODE)
        );
    }

    Condition buildTargetPageCondition(TargetPageQuery query, Table<?> latestExecution) {
        if (query == null) {
            return DSL.trueCondition();
        }

        Condition condition = DSL.trueCondition();
        String targetId = normalize(query.targetId());
        String customerName = normalize(query.customerName());
        String tenantId = normalize(query.tenantId());
        String subscriptionId = normalize(query.subscriptionId());
        String ring = normalize(query.ring());
        String region = normalize(query.region());
        String tier = normalize(query.tier());
        String version = normalize(query.version());
        MappoRuntimeProbeStatus runtimeStatus = query.runtimeStatus();
        MappoTargetStage lastDeploymentStatus = query.lastDeploymentStatus();

        if (!targetId.isBlank()) {
            condition = condition.and(TARGETS.ID.containsIgnoreCase(targetId));
        }
        if (!customerName.isBlank()) {
            condition = condition.and(TARGET_REGISTRATIONS.CUSTOMER_NAME.containsIgnoreCase(customerName));
        }
        if (!tenantId.isBlank()) {
            condition = condition.and(TARGETS.TENANT_ID.cast(String.class).containsIgnoreCase(tenantId));
        }
        if (!subscriptionId.isBlank()) {
            condition = condition.and(TARGETS.SUBSCRIPTION_ID.cast(String.class).containsIgnoreCase(subscriptionId));
        }
        if (!ring.isBlank()) {
            condition = condition.and(tagEqualsCondition("ring", ring));
        }
        if (!region.isBlank()) {
            condition = condition.and(tagEqualsCondition("region", region));
        }
        if (!tier.isBlank()) {
            condition = condition.and(tagEqualsCondition("tier", tier));
        }
        if (!version.isBlank()) {
            condition = condition.and(TARGETS.LAST_DEPLOYED_RELEASE.containsIgnoreCase(version));
        }
        if (runtimeStatus != null) {
            if (runtimeStatus == MappoRuntimeProbeStatus.unknown) {
                condition = condition.and(
                    TARGET_RUNTIME_PROBES.RUNTIME_STATUS.isNull()
                        .or(TARGET_RUNTIME_PROBES.RUNTIME_STATUS.eq(MappoRuntimeProbeStatus.unknown))
                );
            } else {
                condition = condition.and(TARGET_RUNTIME_PROBES.RUNTIME_STATUS.eq(runtimeStatus));
            }
        }
        if (lastDeploymentStatus != null) {
            condition = condition.and(latestExecution.field("latest_status", MappoTargetStage.class).eq(lastDeploymentStatus));
        }
        return condition;
    }

    Condition tagEqualsCondition(String key, String value) {
        var tagFilterTable = TARGET_TAGS.as("tt_" + key);
        return DSL.exists(
            DSL.selectOne()
                .from(tagFilterTable)
                .where(tagFilterTable.TARGET_ID.eq(TARGETS.ID))
                .and(tagFilterTable.TAG_KEY.eq(key))
                .and(tagFilterTable.TAG_VALUE.eq(value))
        );
    }

    int normalizePage(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    int normalizeSize(Integer value) {
        if (value == null || value <= 0) {
            return 25;
        }
        return Math.min(value, 100);
    }

    String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        String text = normalize(value);
        return text.isBlank() ? null : text;
    }
}
