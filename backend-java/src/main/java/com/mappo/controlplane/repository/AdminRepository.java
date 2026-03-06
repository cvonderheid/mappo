package com.mappo.controlplane.repository;

import com.mappo.controlplane.util.JsonUtil;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public AdminRepository(DSLContext dsl, JsonUtil jsonUtil) {
        this.dsl = dsl;
        this.jsonUtil = jsonUtil;
    }

    public List<Map<String, Object>> listMarketplaceEvents(int limit) {
        Result<Record> rows = dsl.fetch(
            "select id, event_type, status::text as status, message, target_id, tenant_id::text as tenant_id, "
                + "subscription_id::text as subscription_id, payload::text as payload, created_at, processed_at "
                + "from marketplace_events order by created_at desc limit ?",
            limit
        );
        List<Map<String, Object>> events = new ArrayList<>();
        for (Record row : rows) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", row.get("id", String.class));
            event.put("event_type", row.get("event_type", String.class));
            event.put("status", row.get("status", String.class));
            event.put("message", row.get("message", String.class));
            event.put("target_id", row.get("target_id", String.class));
            event.put("tenant_id", row.get("tenant_id", String.class));
            event.put("subscription_id", row.get("subscription_id", String.class));
            event.put("payload", jsonUtil.readMap(row.get("payload", String.class)));
            event.put("created_at", row.get("created_at", OffsetDateTime.class));
            event.put("processed_at", row.get("processed_at", OffsetDateTime.class));
            events.add(event);
        }
        return events;
    }

    public List<Map<String, Object>> listForwarderLogs(int limit) {
        Result<Record> rows = dsl.fetch(
            "select id, level::text as level, message, event_id, event_type, target_id, tenant_id::text as tenant_id, "
                + "subscription_id::text as subscription_id, function_app_name, forwarder_request_id, backend_status_code, "
                + "details::text as details, created_at "
                + "from forwarder_logs order by created_at desc limit ?",
            limit
        );
        List<Map<String, Object>> logs = new ArrayList<>();
        for (Record row : rows) {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("log_id", row.get("id", String.class));
            log.put("level", row.get("level", String.class));
            log.put("message", row.get("message", String.class));
            log.put("event_id", row.get("event_id", String.class));
            log.put("event_type", row.get("event_type", String.class));
            log.put("target_id", row.get("target_id", String.class));
            log.put("tenant_id", row.get("tenant_id", String.class));
            log.put("subscription_id", row.get("subscription_id", String.class));
            log.put("function_app_name", row.get("function_app_name", String.class));
            log.put("forwarder_request_id", row.get("forwarder_request_id", String.class));
            log.put("backend_status_code", row.get("backend_status_code", Integer.class));
            log.put("details", jsonUtil.readMap(row.get("details", String.class)));
            log.put("created_at", row.get("created_at", OffsetDateTime.class));
            logs.add(log);
        }
        return logs;
    }

    public boolean marketplaceEventExists(String eventId) {
        Object exists = dsl.fetchValue(
            "select 1 from marketplace_events where id = ?",
            Integer.class,
            eventId
        );
        return exists != null;
    }

    public void saveMarketplaceEvent(
        String eventId,
        String eventType,
        String status,
        String message,
        String targetId,
        String tenantId,
        String subscriptionId,
        Map<String, Object> payload
    ) {
        dsl.query(
            "insert into marketplace_events (id, event_type, status, message, target_id, tenant_id, subscription_id, payload, created_at, processed_at) "
                + "values (?, ?, ?::mappo_marketplace_event_status, ?, ?, ?::uuid, ?::uuid, ?::jsonb, ?, ?)",
            eventId,
            eventType,
            status,
            message,
            targetId,
            tenantId,
            subscriptionId,
            jsonUtil.write(payload),
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC)
        ).execute();
    }

    public boolean forwarderLogExists(String logId) {
        Object exists = dsl.fetchValue(
            "select 1 from forwarder_logs where id = ?",
            Integer.class,
            logId
        );
        return exists != null;
    }

    public void saveForwarderLog(Map<String, Object> request) {
        dsl.query(
            "insert into forwarder_logs (id, level, message, event_id, event_type, target_id, tenant_id, "
                + "subscription_id, function_app_name, forwarder_request_id, backend_status_code, details, created_at) "
                + "values (?, ?::mappo_forwarder_log_level, ?, ?, ?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?::jsonb, ?)",
            request.get("log_id"),
            request.getOrDefault("level", "error"),
            request.get("message"),
            request.get("event_id"),
            request.get("event_type"),
            request.get("target_id"),
            request.get("tenant_id"),
            request.get("subscription_id"),
            request.get("function_app_name"),
            request.get("forwarder_request_id"),
            request.get("backend_status_code"),
            jsonUtil.write(request.getOrDefault("details", Map.of())),
            request.getOrDefault("occurred_at", OffsetDateTime.now(ZoneOffset.UTC))
        ).execute();
    }

    public List<Map<String, Object>> listRegistrations() {
        Result<Record> rows = dsl.fetch(
            "select tr.target_id, tr.display_name, tr.managed_application_id, tr.managed_resource_group_id, "
                + "tr.metadata::text as metadata, tr.last_event_id, tr.created_at, tr.updated_at, "
                + "t.tenant_id::text as tenant_id, t.subscription_id::text as subscription_id, t.managed_app_id, t.customer_name "
                + "from target_registrations tr join targets t on t.id = tr.target_id "
                + "order by tr.updated_at desc"
        );

        List<String> targetIds = rows.stream().map(row -> row.get("target_id", String.class)).toList();
        Map<String, Map<String, String>> tags = loadTags(targetIds);

        List<Map<String, Object>> registrations = new ArrayList<>();
        for (Record row : rows) {
            String targetId = row.get("target_id", String.class);
            Map<String, String> registrationTags = tags.getOrDefault(targetId, Map.of());
            Map<String, Object> registration = new LinkedHashMap<>();
            registration.put("target_id", targetId);
            registration.put("tenant_id", row.get("tenant_id", String.class));
            registration.put("subscription_id", row.get("subscription_id", String.class));
            registration.put("managed_application_id", row.get("managed_application_id", String.class));
            registration.put("managed_resource_group_id", row.get("managed_resource_group_id", String.class));
            registration.put("container_app_resource_id", row.get("managed_app_id", String.class));
            registration.put("display_name", row.get("display_name", String.class));
            registration.put("customer_name", row.get("customer_name", String.class));
            registration.put("tags", registrationTags);
            registration.put("metadata", jsonUtil.readMap(row.get("metadata", String.class)));
            registration.put("last_event_id", row.get("last_event_id", String.class));
            registration.put("created_at", row.get("created_at", OffsetDateTime.class));
            registration.put("updated_at", row.get("updated_at", OffsetDateTime.class));
            registrations.add(registration);
        }
        return registrations;
    }

    public Map<String, Object> getRegistration(String targetId) {
        return listRegistrations().stream()
            .filter(row -> targetId.equals(row.get("target_id")))
            .findFirst()
            .orElse(Map.of());
    }

    public void upsertRegistration(Map<String, Object> registration) {
        dsl.query(
            "insert into target_registrations (target_id, display_name, managed_application_id, managed_resource_group_id, metadata, "
                + "last_event_id, created_at, updated_at) "
                + "values (?, ?, ?, ?, ?::jsonb, ?, ?, ?) "
                + "on conflict (target_id) do update set display_name = excluded.display_name, "
                + "managed_application_id = excluded.managed_application_id, managed_resource_group_id = excluded.managed_resource_group_id, "
                + "metadata = excluded.metadata, last_event_id = excluded.last_event_id, updated_at = excluded.updated_at",
            registration.get("target_id"),
            registration.get("display_name"),
            registration.get("managed_application_id"),
            registration.get("managed_resource_group_id"),
            jsonUtil.write(registration.getOrDefault("metadata", Map.of())),
            registration.get("last_event_id"),
            registration.getOrDefault("created_at", OffsetDateTime.now(ZoneOffset.UTC)),
            OffsetDateTime.now(ZoneOffset.UTC)
        ).execute();
    }

    public void deleteRegistration(String targetId) {
        dsl.query("delete from target_registrations where target_id = ?", targetId).execute();
    }

    public void updateRegistrationAndTarget(String targetId, Map<String, Object> patch) {
        Map<String, Object> current = getRegistration(targetId);
        if (current.isEmpty()) {
            return;
        }

        String displayName = patch.containsKey("display_name")
            ? String.valueOf(patch.get("display_name"))
            : String.valueOf(current.get("display_name"));
        String managedApplicationId = patch.containsKey("managed_application_id")
            ? nullableString(patch.get("managed_application_id"))
            : nullableString(current.get("managed_application_id"));
        String managedResourceGroupId = patch.containsKey("managed_resource_group_id")
            ? String.valueOf(patch.get("managed_resource_group_id"))
            : String.valueOf(current.get("managed_resource_group_id"));

        Map<String, Object> metadata = new LinkedHashMap<>((Map<String, Object>) current.getOrDefault("metadata", Map.of()));
        if (patch.get("metadata") instanceof Map<?, ?> metadataPatch) {
            metadata = metadataPatch.entrySet().stream().collect(
                Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()),
                    Map.Entry::getValue,
                    (left, right) -> right,
                    LinkedHashMap::new
                )
            );
        }

        dsl.query(
            "update target_registrations set display_name = ?, managed_application_id = ?, managed_resource_group_id = ?, metadata = ?::jsonb, "
                + "updated_at = ? where target_id = ?",
            displayName,
            managedApplicationId,
            managedResourceGroupId,
            jsonUtil.write(metadata),
            OffsetDateTime.now(ZoneOffset.UTC),
            targetId
        ).execute();

        if (patch.containsKey("customer_name") || patch.containsKey("last_deployed_release") || patch.containsKey("health_status") || patch.containsKey("container_app_resource_id")) {
            String managedAppId = patch.containsKey("container_app_resource_id")
                ? nullableString(patch.get("container_app_resource_id"))
                : nullableString(current.get("container_app_resource_id"));
            String customerName = patch.containsKey("customer_name")
                ? nullableString(patch.get("customer_name"))
                : nullableString(current.get("customer_name"));
            String lastDeployedRelease = patch.containsKey("last_deployed_release")
                ? String.valueOf(patch.get("last_deployed_release"))
                : "unknown";
            String healthStatus = patch.containsKey("health_status")
                ? String.valueOf(patch.get("health_status"))
                : "registered";
            dsl.query(
                "update targets set managed_app_id = ?, customer_name = ?, last_deployed_release = ?, "
                    + "health_status = ?::mappo_health_status, updated_at = ? where id = ?",
                managedAppId,
                customerName,
                lastDeployedRelease,
                healthStatus,
                OffsetDateTime.now(ZoneOffset.UTC),
                targetId
            ).execute();
        }

        if (patch.containsKey("tags") && patch.get("tags") instanceof Map<?, ?> tagPatch) {
            dsl.query("delete from target_tags where target_id = ?", targetId).execute();
            for (Map.Entry<?, ?> entry : tagPatch.entrySet()) {
                String key = String.valueOf(entry.getKey()).trim();
                if (key.isEmpty()) {
                    continue;
                }
                dsl.query(
                    "insert into target_tags (target_id, tag_key, tag_value) values (?, ?, ?)",
                    targetId,
                    key,
                    String.valueOf(entry.getValue())
                ).execute();
            }
        }
    }

    private String nullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Map<String, String>> loadTags(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = targetIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Result<Record> rows = dsl.fetch(
            "select target_id, tag_key, tag_value from target_tags where target_id in (" + placeholders + ")",
            targetIds.toArray()
        );
        Map<String, Map<String, String>> tags = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get("target_id", String.class);
            tags.computeIfAbsent(targetId, key -> new LinkedHashMap<>())
                .put(row.get("tag_key", String.class), row.get("tag_value", String.class));
        }
        return tags;
    }
}
