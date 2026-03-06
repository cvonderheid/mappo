package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.command.TargetUpsertCommand;
import com.mappo.controlplane.model.TargetRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetRepository {

    private final DSLContext dsl;

    public List<TargetRecord> listTargets(Map<String, String> filters) {
        Condition condition = DSL.trueCondition();
        if (filters != null) {
            var tagFilterTable = TARGET_TAGS.as("tt");
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String key = normalize(entry.getKey());
                String value = normalize(entry.getValue());
                if (key.isBlank() || value.isBlank()) {
                    continue;
                }
                condition = condition.andExists(
                    DSL.selectOne()
                        .from(tagFilterTable)
                        .where(tagFilterTable.TARGET_ID.eq(TARGETS.ID))
                        .and(tagFilterTable.TAG_KEY.eq(key))
                        .and(tagFilterTable.TAG_VALUE.eq(value))
                );
            }
        }

        var rows = dsl.select(
                TARGETS.ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGETS.LAST_CHECK_IN_AT,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .leftJoin(TARGET_REGISTRATIONS)
            .on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
            .where(condition)
            .orderBy(TARGETS.ID.asc())
            .fetch();

        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> targetIds = rows.stream().map(row -> row.get(TARGETS.ID)).toList();
        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);

        List<TargetRecord> targets = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGETS.ID);
            targets.add(toTargetRecord(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }
        return targets;
    }

    public Optional<TargetRecord> getTarget(String targetId) {
        Record row = dsl.select(
                TARGETS.ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGETS.LAST_CHECK_IN_AT,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .leftJoin(TARGET_REGISTRATIONS)
            .on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
            .where(TARGETS.ID.eq(targetId))
            .fetchOne();

        if (row == null) {
            return Optional.empty();
        }

        Map<String, String> tags = loadTags(List.of(targetId)).getOrDefault(targetId, Map.of());
        return Optional.of(toTargetRecord(row, tags));
    }

    public List<TargetRecord> getTargetsByIds(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }

        var rows = dsl.select(
                TARGETS.ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGETS.LAST_CHECK_IN_AT,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .leftJoin(TARGET_REGISTRATIONS)
            .on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
            .where(TARGETS.ID.in(targetIds))
            .orderBy(TARGETS.ID.asc())
            .fetch();

        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);
        List<TargetRecord> targets = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGETS.ID);
            targets.add(toTargetRecord(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }
        return targets;
    }

    public List<TargetRecord> getTargetsByTagFilters(Map<String, String> filters) {
        return listTargets(filters == null ? Map.of() : filters);
    }

    public List<TargetExecutionContextRecord> getExecutionContextsByIds(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }

        var rows = dsl.select(
                TARGETS.ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGETS.TENANT_ID,
                TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .join(TARGET_REGISTRATIONS)
            .on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
            .where(TARGETS.ID.in(targetIds))
            .orderBy(TARGETS.ID.asc())
            .fetch();

        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);
        List<TargetExecutionContextRecord> contexts = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGETS.ID);
            contexts.add(new TargetExecutionContextRecord(
                targetId,
                row.get(TARGETS.SUBSCRIPTION_ID),
                row.get(TARGETS.TENANT_ID),
                row.get(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID),
                row.get(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID),
                tagsByTarget.getOrDefault(targetId, Map.of()),
                row.get(TARGETS.SIMULATED_FAILURE_MODE)
            ));
        }
        return contexts;
    }

    public void upsertTarget(TargetUpsertCommand target) {
        String targetId = requiredText(target.id(), "id");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String release = normalize(defaultIfBlank(target.lastDeployedRelease(), "unknown"));
        if (release.isBlank()) {
            release = "unknown";
        }

        dsl.insertInto(TARGETS)
            .set(TARGETS.ID, targetId)
            .set(TARGETS.TENANT_ID, requiredUuid(target.tenantId(), "tenant_id"))
            .set(TARGETS.SUBSCRIPTION_ID, requiredUuid(target.subscriptionId(), "subscription_id"))
            .set(TARGETS.LAST_DEPLOYED_RELEASE, release)
            .set(TARGETS.HEALTH_STATUS, enumOrDefault(target.healthStatus(), MappoHealthStatus.registered))
            .set(TARGETS.LAST_CHECK_IN_AT, toTimestamp(target.lastCheckInAt(), now))
            .set(TARGETS.SIMULATED_FAILURE_MODE, enumOrDefault(target.simulatedFailureMode(), MappoSimulatedFailureMode.none))
            .set(TARGETS.UPDATED_AT, now)
            .onConflict(TARGETS.ID)
            .doUpdate()
            .set(TARGETS.TENANT_ID, requiredUuid(target.tenantId(), "tenant_id"))
            .set(TARGETS.SUBSCRIPTION_ID, requiredUuid(target.subscriptionId(), "subscription_id"))
            .set(TARGETS.LAST_DEPLOYED_RELEASE, release)
            .set(TARGETS.HEALTH_STATUS, enumOrDefault(target.healthStatus(), MappoHealthStatus.registered))
            .set(TARGETS.LAST_CHECK_IN_AT, toTimestamp(target.lastCheckInAt(), now))
            .set(TARGETS.SIMULATED_FAILURE_MODE, enumOrDefault(target.simulatedFailureMode(), MappoSimulatedFailureMode.none))
            .set(TARGETS.UPDATED_AT, now)
            .execute();

        replaceTags(targetId, target.tags());
    }

    public void updateTargetHealth(String targetId, MappoHealthStatus healthStatus) {
        dsl.update(TARGETS)
            .set(TARGETS.HEALTH_STATUS, enumOrDefault(healthStatus, MappoHealthStatus.registered))
            .set(TARGETS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(TARGETS.ID.eq(targetId))
            .execute();
    }

    public void updateLastDeployedRelease(String targetId, String releaseVersion) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.update(TARGETS)
            .set(TARGETS.LAST_DEPLOYED_RELEASE, normalize(releaseVersion))
            .set(TARGETS.HEALTH_STATUS, MappoHealthStatus.healthy)
            .set(TARGETS.LAST_CHECK_IN_AT, now)
            .set(TARGETS.UPDATED_AT, now)
            .where(TARGETS.ID.eq(targetId))
            .execute();
    }

    public void deleteTarget(String targetId) {
        dsl.deleteFrom(TARGETS)
            .where(TARGETS.ID.eq(targetId))
            .execute();
    }

    private void replaceTags(String targetId, Map<String, String> tags) {
        dsl.deleteFrom(TARGET_TAGS)
            .where(TARGET_TAGS.TARGET_ID.eq(targetId))
            .execute();

        if (tags.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            dsl.insertInto(TARGET_TAGS)
                .set(TARGET_TAGS.TARGET_ID, targetId)
                .set(TARGET_TAGS.TAG_KEY, entry.getKey())
                .set(TARGET_TAGS.TAG_VALUE, entry.getValue())
                .execute();
        }
    }

    private Map<String, Map<String, String>> loadTags(List<String> targetIds) {
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

    private TargetRecord toTargetRecord(Record row, Map<String, String> tags) {
        return new TargetRecord(
            row.get(TARGETS.ID),
            row.get(TARGETS.TENANT_ID),
            row.get(TARGETS.SUBSCRIPTION_ID),
            row.get(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID),
            row.get(TARGET_REGISTRATIONS.CUSTOMER_NAME),
            tags,
            row.get(TARGETS.LAST_DEPLOYED_RELEASE),
            row.get(TARGETS.HEALTH_STATUS),
            row.get(TARGETS.LAST_CHECK_IN_AT),
            row.get(TARGETS.SIMULATED_FAILURE_MODE)
        );
    }

    private OffsetDateTime toTimestamp(Object value, OffsetDateTime fallback) {
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp;
        }
        return fallback;
    }

    private <T> T enumOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private UUID requiredUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String requiredText(Object value, String field) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }

    private String nullableText(Object value) {
        String text = normalize(value);
        return text.isBlank() ? null : text;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
