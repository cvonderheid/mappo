package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.EnumType;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetRepository {

    private final DSLContext dsl;

    public List<Map<String, Object>> listTargets(Map<String, String> filters) {
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
                TARGETS.MANAGED_APP_ID,
                TARGETS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGETS.LAST_CHECK_IN_AT,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .where(condition)
            .orderBy(TARGETS.ID.asc())
            .fetch();

        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> targetIds = rows.stream().map(row -> row.get(TARGETS.ID)).toList();
        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);

        List<Map<String, Object>> targets = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGETS.ID);
            targets.add(toTargetMap(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }
        return targets;
    }

    public Map<String, Object> getTarget(String targetId) {
        Record row = dsl.select(
                TARGETS.ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGETS.MANAGED_APP_ID,
                TARGETS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGETS.LAST_CHECK_IN_AT,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .where(TARGETS.ID.eq(targetId))
            .fetchOne();

        if (row == null) {
            return Map.of();
        }

        Map<String, String> tags = loadTags(List.of(targetId)).getOrDefault(targetId, Map.of());
        return toTargetMap(row, tags);
    }

    public List<Map<String, Object>> getTargetsByIds(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }

        var rows = dsl.select(
                TARGETS.ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGETS.MANAGED_APP_ID,
                TARGETS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGETS.LAST_CHECK_IN_AT,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .where(TARGETS.ID.in(targetIds))
            .orderBy(TARGETS.ID.asc())
            .fetch();

        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);
        List<Map<String, Object>> targets = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGETS.ID);
            targets.add(toTargetMap(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }
        return targets;
    }

    public List<Map<String, Object>> getTargetsByTagFilters(Map<String, String> filters) {
        return listTargets(filters == null ? Map.of() : filters);
    }

    public void upsertTarget(Map<String, Object> target) {
        String targetId = requiredText(target.get("id"), "id");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String release = normalize(target.getOrDefault("last_deployed_release", "unknown"));
        if (release.isBlank()) {
            release = "unknown";
        }

        dsl.insertInto(TARGETS)
            .set(TARGETS.ID, targetId)
            .set(TARGETS.TENANT_ID, requiredUuid(target.get("tenant_id"), "tenant_id"))
            .set(TARGETS.SUBSCRIPTION_ID, requiredUuid(target.get("subscription_id"), "subscription_id"))
            .set(TARGETS.MANAGED_APP_ID, requiredText(target.get("managed_app_id"), "managed_app_id"))
            .set(TARGETS.CUSTOMER_NAME, nullableText(target.get("customer_name")))
            .set(TARGETS.LAST_DEPLOYED_RELEASE, release)
            .set(TARGETS.HEALTH_STATUS, toHealthStatus(target.get("health_status"), MappoHealthStatus.registered))
            .set(TARGETS.LAST_CHECK_IN_AT, toTimestamp(target.get("last_check_in_at"), now))
            .set(TARGETS.SIMULATED_FAILURE_MODE, toFailureMode(target.get("simulated_failure_mode"), MappoSimulatedFailureMode.none))
            .set(TARGETS.UPDATED_AT, now)
            .onConflict(TARGETS.ID)
            .doUpdate()
            .set(TARGETS.TENANT_ID, requiredUuid(target.get("tenant_id"), "tenant_id"))
            .set(TARGETS.SUBSCRIPTION_ID, requiredUuid(target.get("subscription_id"), "subscription_id"))
            .set(TARGETS.MANAGED_APP_ID, requiredText(target.get("managed_app_id"), "managed_app_id"))
            .set(TARGETS.CUSTOMER_NAME, nullableText(target.get("customer_name")))
            .set(TARGETS.LAST_DEPLOYED_RELEASE, release)
            .set(TARGETS.HEALTH_STATUS, toHealthStatus(target.get("health_status"), MappoHealthStatus.registered))
            .set(TARGETS.LAST_CHECK_IN_AT, toTimestamp(target.get("last_check_in_at"), now))
            .set(TARGETS.SIMULATED_FAILURE_MODE, toFailureMode(target.get("simulated_failure_mode"), MappoSimulatedFailureMode.none))
            .set(TARGETS.UPDATED_AT, now)
            .execute();

        replaceTags(targetId, castTags(target.get("tags")));
    }

    public void updateTargetHealth(String targetId, String healthStatus) {
        dsl.update(TARGETS)
            .set(TARGETS.HEALTH_STATUS, toHealthStatus(healthStatus, MappoHealthStatus.registered))
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

    private Map<String, Object> toTargetMap(Record row, Map<String, String> tags) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("id", row.get(TARGETS.ID));
        target.put("tenant_id", uuidText(row.get(TARGETS.TENANT_ID)));
        target.put("subscription_id", uuidText(row.get(TARGETS.SUBSCRIPTION_ID)));
        target.put("managed_app_id", row.get(TARGETS.MANAGED_APP_ID));
        target.put("customer_name", row.get(TARGETS.CUSTOMER_NAME));
        target.put("tags", tags);
        target.put("last_deployed_release", row.get(TARGETS.LAST_DEPLOYED_RELEASE));
        target.put("health_status", enumLiteral(row.get(TARGETS.HEALTH_STATUS)));
        target.put("last_check_in_at", row.get(TARGETS.LAST_CHECK_IN_AT));
        target.put("simulated_failure_mode", enumLiteral(row.get(TARGETS.SIMULATED_FAILURE_MODE)));
        return target;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> castTags(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }

        Map<String, String> tags = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = normalize(entry.getKey());
            String val = normalize(entry.getValue());
            if (!key.isBlank()) {
                tags.put(key, val);
            }
        }
        return tags;
    }

    private String uuidText(UUID value) {
        return value == null ? null : value.toString();
    }

    private String enumLiteral(EnumType value) {
        return value == null ? null : value.getLiteral();
    }

    private OffsetDateTime toTimestamp(Object value, OffsetDateTime fallback) {
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp;
        }
        return fallback;
    }

    private MappoHealthStatus toHealthStatus(Object value, MappoHealthStatus fallback) {
        String text = normalize(value);
        if (text.isBlank()) {
            return fallback;
        }
        MappoHealthStatus parsed = MappoHealthStatus.lookupLiteral(text);
        return parsed == null ? fallback : parsed;
    }

    private MappoSimulatedFailureMode toFailureMode(Object value, MappoSimulatedFailureMode fallback) {
        String text = normalize(value);
        if (text.isBlank()) {
            return fallback;
        }
        MappoSimulatedFailureMode parsed = MappoSimulatedFailureMode.lookupLiteral(text);
        return parsed == null ? fallback : parsed;
    }

    private UUID requiredUuid(Object value, String field) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(text);
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
}
