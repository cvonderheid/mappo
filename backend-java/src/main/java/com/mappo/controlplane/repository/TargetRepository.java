package com.mappo.controlplane.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

@Repository
public class TargetRepository {

    private final DSLContext dsl;

    public TargetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Map<String, Object>> listTargets(Map<String, String> filters) {
        StringBuilder sql = new StringBuilder(
            "select t.id, t.tenant_id::text as tenant_id, t.subscription_id::text as subscription_id, "
                + "t.managed_app_id, t.customer_name, t.last_deployed_release, "
                + "t.health_status::text as health_status, t.last_check_in_at, "
                + "t.simulated_failure_mode::text as simulated_failure_mode "
                + "from targets t where 1=1"
        );

        List<Object> bindings = new ArrayList<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            sql.append(" and exists (select 1 from target_tags tt where tt.target_id = t.id and tt.tag_key = ? and tt.tag_value = ?)");
            bindings.add(entry.getKey());
            bindings.add(entry.getValue());
        }
        sql.append(" order by t.id asc");

        Result<Record> rows = dsl.fetch(sql.toString(), bindings.toArray());
        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> ids = rows.stream().map(row -> row.get("id", String.class)).toList();
        Map<String, Map<String, String>> tagsByTarget = loadTags(ids);

        List<Map<String, Object>> targets = new ArrayList<>();
        for (Record row : rows) {
            String targetId = row.get("id", String.class);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("id", targetId);
            target.put("tenant_id", row.get("tenant_id", String.class));
            target.put("subscription_id", row.get("subscription_id", String.class));
            target.put("managed_app_id", row.get("managed_app_id", String.class));
            target.put("customer_name", row.get("customer_name", String.class));
            target.put("tags", tagsByTarget.getOrDefault(targetId, Map.of()));
            target.put("last_deployed_release", row.get("last_deployed_release", String.class));
            target.put("health_status", row.get("health_status", String.class));
            target.put("last_check_in_at", row.get("last_check_in_at", OffsetDateTime.class));
            target.put("simulated_failure_mode", row.get("simulated_failure_mode", String.class));
            targets.add(target);
        }
        return targets;
    }

    public Map<String, Object> getTarget(String targetId) {
        Result<Record> rows = dsl.fetch(
            "select t.id, t.tenant_id::text as tenant_id, t.subscription_id::text as subscription_id, "
                + "t.managed_app_id, t.customer_name, t.last_deployed_release, "
                + "t.health_status::text as health_status, t.last_check_in_at, "
                + "t.simulated_failure_mode::text as simulated_failure_mode "
                + "from targets t where t.id = ?",
            targetId
        );
        if (rows.isEmpty()) {
            return Map.of();
        }
        Record row = rows.getFirst();
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("id", row.get("id", String.class));
        target.put("tenant_id", row.get("tenant_id", String.class));
        target.put("subscription_id", row.get("subscription_id", String.class));
        target.put("managed_app_id", row.get("managed_app_id", String.class));
        target.put("customer_name", row.get("customer_name", String.class));
        target.put("tags", loadTags(List.of(targetId)).getOrDefault(targetId, Map.of()));
        target.put("last_deployed_release", row.get("last_deployed_release", String.class));
        target.put("health_status", row.get("health_status", String.class));
        target.put("last_check_in_at", row.get("last_check_in_at", OffsetDateTime.class));
        target.put("simulated_failure_mode", row.get("simulated_failure_mode", String.class));
        return target;
    }

    public List<Map<String, Object>> getTargetsByIds(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        String placeholders = targetIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Result<Record> rows = dsl.fetch(
            "select t.id, t.tenant_id::text as tenant_id, t.subscription_id::text as subscription_id, "
                + "t.managed_app_id, t.customer_name, t.last_deployed_release, "
                + "t.health_status::text as health_status, t.last_check_in_at, "
                + "t.simulated_failure_mode::text as simulated_failure_mode "
                + "from targets t where t.id in (" + placeholders + ")",
            targetIds.toArray()
        );

        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);
        List<Map<String, Object>> targets = new ArrayList<>();
        for (Record row : rows) {
            String targetId = row.get("id", String.class);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("id", targetId);
            target.put("tenant_id", row.get("tenant_id", String.class));
            target.put("subscription_id", row.get("subscription_id", String.class));
            target.put("managed_app_id", row.get("managed_app_id", String.class));
            target.put("customer_name", row.get("customer_name", String.class));
            target.put("tags", tagsByTarget.getOrDefault(targetId, Map.of()));
            target.put("last_deployed_release", row.get("last_deployed_release", String.class));
            target.put("health_status", row.get("health_status", String.class));
            target.put("last_check_in_at", row.get("last_check_in_at", OffsetDateTime.class));
            target.put("simulated_failure_mode", row.get("simulated_failure_mode", String.class));
            targets.add(target);
        }
        return targets;
    }

    public List<Map<String, Object>> getTargetsByTagFilters(Map<String, String> filters) {
        return listTargets(filters == null ? Map.of() : filters);
    }

    public void upsertTarget(Map<String, Object> target) {
        dsl.query(
            "insert into targets (id, tenant_id, subscription_id, managed_app_id, customer_name, "
                + "last_deployed_release, health_status, last_check_in_at, simulated_failure_mode, updated_at) "
                + "values (?, ?::uuid, ?::uuid, ?, ?, ?, ?::mappo_health_status, ?, ?::mappo_simulated_failure_mode, ?) "
                + "on conflict (id) do update set "
                + "tenant_id = excluded.tenant_id, subscription_id = excluded.subscription_id, managed_app_id = excluded.managed_app_id, "
                + "customer_name = excluded.customer_name, last_deployed_release = excluded.last_deployed_release, "
                + "health_status = excluded.health_status, last_check_in_at = excluded.last_check_in_at, "
                + "simulated_failure_mode = excluded.simulated_failure_mode, updated_at = excluded.updated_at",
            target.get("id"),
            target.get("tenant_id"),
            target.get("subscription_id"),
            target.get("managed_app_id"),
            target.get("customer_name"),
            target.getOrDefault("last_deployed_release", "unknown"),
            target.getOrDefault("health_status", "registered"),
            target.getOrDefault("last_check_in_at", OffsetDateTime.now(ZoneOffset.UTC)),
            target.getOrDefault("simulated_failure_mode", "none"),
            OffsetDateTime.now(ZoneOffset.UTC)
        ).execute();

        replaceTags(String.valueOf(target.get("id")), castTags(target.get("tags")));
    }

    public void updateTargetHealth(String targetId, String healthStatus) {
        dsl.query(
            "update targets set health_status = ?::mappo_health_status, updated_at = ? where id = ?",
            healthStatus,
            OffsetDateTime.now(ZoneOffset.UTC),
            targetId
        ).execute();
    }

    public void updateLastDeployedRelease(String targetId, String releaseVersion) {
        dsl.query(
            "update targets set last_deployed_release = ?, health_status = 'healthy'::mappo_health_status, "
                + "last_check_in_at = ?, updated_at = ? where id = ?",
            releaseVersion,
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC),
            targetId
        ).execute();
    }

    public void deleteTarget(String targetId) {
        dsl.query("delete from targets where id = ?", targetId).execute();
    }

    private void replaceTags(String targetId, Map<String, String> tags) {
        dsl.query("delete from target_tags where target_id = ?", targetId).execute();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            dsl.query(
                "insert into target_tags (target_id, tag_key, tag_value) values (?, ?, ?)",
                targetId,
                entry.getKey(),
                entry.getValue()
            ).execute();
        }
    }

    private Map<String, Map<String, String>> loadTags(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = targetIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Result<Record> tagRows = dsl.fetch(
            "select target_id, tag_key, tag_value from target_tags where target_id in (" + placeholders + ")",
            targetIds.toArray()
        );
        Map<String, Map<String, String>> tags = new HashMap<>();
        for (Record row : tagRows) {
            String targetId = row.get("target_id", String.class);
            tags.computeIfAbsent(targetId, key -> new LinkedHashMap<>())
                .put(row.get("tag_key", String.class), row.get("tag_value", String.class));
        }
        return tags;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> castTags(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String val = String.valueOf(entry.getValue());
            if (!key.isBlank()) {
                tags.put(key, val);
            }
        }
        return tags;
    }
}
