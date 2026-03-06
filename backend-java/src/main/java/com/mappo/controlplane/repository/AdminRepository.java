package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.FORWARDER_LOGS;
import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.util.JsonUtil;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public List<Map<String, Object>> listMarketplaceEvents(int limit) {
        var rows = dsl.select(
                MARKETPLACE_EVENTS.ID,
                MARKETPLACE_EVENTS.EVENT_TYPE,
                MARKETPLACE_EVENTS.STATUS,
                MARKETPLACE_EVENTS.MESSAGE,
                MARKETPLACE_EVENTS.TARGET_ID,
                MARKETPLACE_EVENTS.TENANT_ID,
                MARKETPLACE_EVENTS.SUBSCRIPTION_ID,
                MARKETPLACE_EVENTS.PAYLOAD,
                MARKETPLACE_EVENTS.CREATED_AT,
                MARKETPLACE_EVENTS.PROCESSED_AT
            )
            .from(MARKETPLACE_EVENTS)
            .orderBy(MARKETPLACE_EVENTS.CREATED_AT.desc())
            .limit(Math.max(1, limit))
            .fetch();

        List<Map<String, Object>> events = new ArrayList<>(rows.size());
        for (Record row : rows) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", row.get(MARKETPLACE_EVENTS.ID));
            event.put("event_type", row.get(MARKETPLACE_EVENTS.EVENT_TYPE));
            event.put("status", literal(row.get(MARKETPLACE_EVENTS.STATUS)));
            event.put("message", row.get(MARKETPLACE_EVENTS.MESSAGE));
            event.put("target_id", row.get(MARKETPLACE_EVENTS.TARGET_ID));
            event.put("tenant_id", uuidText(row.get(MARKETPLACE_EVENTS.TENANT_ID)));
            event.put("subscription_id", uuidText(row.get(MARKETPLACE_EVENTS.SUBSCRIPTION_ID)));
            event.put("payload", parseJsonMap(row.get(MARKETPLACE_EVENTS.PAYLOAD)));
            event.put("created_at", row.get(MARKETPLACE_EVENTS.CREATED_AT));
            event.put("processed_at", row.get(MARKETPLACE_EVENTS.PROCESSED_AT));
            events.add(event);
        }
        return events;
    }

    public List<Map<String, Object>> listForwarderLogs(int limit) {
        var rows = dsl.select(
                FORWARDER_LOGS.ID,
                FORWARDER_LOGS.LEVEL,
                FORWARDER_LOGS.MESSAGE,
                FORWARDER_LOGS.EVENT_ID,
                FORWARDER_LOGS.EVENT_TYPE,
                FORWARDER_LOGS.TARGET_ID,
                FORWARDER_LOGS.TENANT_ID,
                FORWARDER_LOGS.SUBSCRIPTION_ID,
                FORWARDER_LOGS.FUNCTION_APP_NAME,
                FORWARDER_LOGS.FORWARDER_REQUEST_ID,
                FORWARDER_LOGS.BACKEND_STATUS_CODE,
                FORWARDER_LOGS.DETAILS,
                FORWARDER_LOGS.CREATED_AT
            )
            .from(FORWARDER_LOGS)
            .orderBy(FORWARDER_LOGS.CREATED_AT.desc())
            .limit(Math.max(1, limit))
            .fetch();

        List<Map<String, Object>> logs = new ArrayList<>(rows.size());
        for (Record row : rows) {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("log_id", row.get(FORWARDER_LOGS.ID));
            log.put("level", literal(row.get(FORWARDER_LOGS.LEVEL)));
            log.put("message", row.get(FORWARDER_LOGS.MESSAGE));
            log.put("event_id", row.get(FORWARDER_LOGS.EVENT_ID));
            log.put("event_type", row.get(FORWARDER_LOGS.EVENT_TYPE));
            log.put("target_id", row.get(FORWARDER_LOGS.TARGET_ID));
            log.put("tenant_id", uuidText(row.get(FORWARDER_LOGS.TENANT_ID)));
            log.put("subscription_id", uuidText(row.get(FORWARDER_LOGS.SUBSCRIPTION_ID)));
            log.put("function_app_name", row.get(FORWARDER_LOGS.FUNCTION_APP_NAME));
            log.put("forwarder_request_id", row.get(FORWARDER_LOGS.FORWARDER_REQUEST_ID));
            log.put("backend_status_code", row.get(FORWARDER_LOGS.BACKEND_STATUS_CODE));
            log.put("details", parseJsonMap(row.get(FORWARDER_LOGS.DETAILS)));
            log.put("created_at", row.get(FORWARDER_LOGS.CREATED_AT));
            logs.add(log);
        }
        return logs;
    }

    public boolean marketplaceEventExists(String eventId) {
        return dsl.fetchExists(
            dsl.selectOne()
                .from(MARKETPLACE_EVENTS)
                .where(MARKETPLACE_EVENTS.ID.eq(eventId))
        );
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
        dsl.insertInto(MARKETPLACE_EVENTS)
            .set(MARKETPLACE_EVENTS.ID, eventId)
            .set(MARKETPLACE_EVENTS.EVENT_TYPE, eventType)
            .set(
                MARKETPLACE_EVENTS.STATUS,
                enumOrDefault(MappoMarketplaceEventStatus.lookupLiteral(status), MappoMarketplaceEventStatus.applied)
            )
            .set(MARKETPLACE_EVENTS.MESSAGE, message)
            .set(MARKETPLACE_EVENTS.TARGET_ID, nullableText(targetId))
            .set(MARKETPLACE_EVENTS.TENANT_ID, requiredUuid(tenantId, "tenant_id"))
            .set(MARKETPLACE_EVENTS.SUBSCRIPTION_ID, requiredUuid(subscriptionId, "subscription_id"))
            .set(MARKETPLACE_EVENTS.PAYLOAD, toJson(payload == null ? Map.of() : payload))
            .set(MARKETPLACE_EVENTS.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .set(MARKETPLACE_EVENTS.PROCESSED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute();
    }

    public boolean forwarderLogExists(String logId) {
        return dsl.fetchExists(
            dsl.selectOne()
                .from(FORWARDER_LOGS)
                .where(FORWARDER_LOGS.ID.eq(logId))
        );
    }

    public void saveForwarderLog(Map<String, Object> request) {
        dsl.insertInto(FORWARDER_LOGS)
            .set(FORWARDER_LOGS.ID, normalize(request.get("log_id")))
            .set(
                FORWARDER_LOGS.LEVEL,
                enumOrDefault(
                    MappoForwarderLogLevel.lookupLiteral(normalize(request.getOrDefault("level", "error"))),
                    MappoForwarderLogLevel.error
                )
            )
            .set(FORWARDER_LOGS.MESSAGE, normalize(request.get("message")))
            .set(FORWARDER_LOGS.EVENT_ID, nullableText(request.get("event_id")))
            .set(FORWARDER_LOGS.EVENT_TYPE, nullableText(request.get("event_type")))
            .set(FORWARDER_LOGS.TARGET_ID, nullableText(request.get("target_id")))
            .set(FORWARDER_LOGS.TENANT_ID, optionalUuid(request.get("tenant_id")))
            .set(FORWARDER_LOGS.SUBSCRIPTION_ID, optionalUuid(request.get("subscription_id")))
            .set(FORWARDER_LOGS.FUNCTION_APP_NAME, nullableText(request.get("function_app_name")))
            .set(FORWARDER_LOGS.FORWARDER_REQUEST_ID, nullableText(request.get("forwarder_request_id")))
            .set(FORWARDER_LOGS.BACKEND_STATUS_CODE, asInteger(request.get("backend_status_code")))
            .set(FORWARDER_LOGS.DETAILS, toJson(request.getOrDefault("details", Map.of())))
            .set(FORWARDER_LOGS.CREATED_AT, toTimestamp(request.get("occurred_at"), OffsetDateTime.now(ZoneOffset.UTC)))
            .execute();
    }

    public List<Map<String, Object>> listRegistrations() {
        var rows = dsl.select(
                TARGET_REGISTRATIONS.TARGET_ID,
                TARGET_REGISTRATIONS.DISPLAY_NAME,
                TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID,
                TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID,
                TARGET_REGISTRATIONS.METADATA,
                TARGET_REGISTRATIONS.LAST_EVENT_ID,
                TARGET_REGISTRATIONS.CREATED_AT,
                TARGET_REGISTRATIONS.UPDATED_AT,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGETS.MANAGED_APP_ID,
                TARGETS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS
            )
            .from(TARGET_REGISTRATIONS)
            .join(TARGETS)
            .on(TARGETS.ID.eq(TARGET_REGISTRATIONS.TARGET_ID))
            .orderBy(TARGET_REGISTRATIONS.UPDATED_AT.desc())
            .fetch();

        List<String> targetIds = rows.stream().map(row -> row.get(TARGET_REGISTRATIONS.TARGET_ID)).toList();
        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);

        List<Map<String, Object>> registrations = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGET_REGISTRATIONS.TARGET_ID);
            registrations.add(toRegistrationMap(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }
        return registrations;
    }

    public Map<String, Object> getRegistration(String targetId) {
        Record row = dsl.select(
                TARGET_REGISTRATIONS.TARGET_ID,
                TARGET_REGISTRATIONS.DISPLAY_NAME,
                TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID,
                TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID,
                TARGET_REGISTRATIONS.METADATA,
                TARGET_REGISTRATIONS.LAST_EVENT_ID,
                TARGET_REGISTRATIONS.CREATED_AT,
                TARGET_REGISTRATIONS.UPDATED_AT,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGETS.MANAGED_APP_ID,
                TARGETS.CUSTOMER_NAME,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS
            )
            .from(TARGET_REGISTRATIONS)
            .join(TARGETS)
            .on(TARGETS.ID.eq(TARGET_REGISTRATIONS.TARGET_ID))
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .fetchOne();

        if (row == null) {
            return Map.of();
        }

        Map<String, String> tags = loadTags(List.of(targetId)).getOrDefault(targetId, Map.of());
        return toRegistrationMap(row, tags);
    }

    public void upsertRegistration(Map<String, Object> registration) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime createdAt = toTimestamp(registration.get("created_at"), now);

        dsl.insertInto(TARGET_REGISTRATIONS)
            .set(TARGET_REGISTRATIONS.TARGET_ID, normalize(registration.get("target_id")))
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, normalize(registration.get("display_name")))
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, nullableText(registration.get("managed_application_id")))
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, normalize(registration.get("managed_resource_group_id")))
            .set(TARGET_REGISTRATIONS.METADATA, toJson(registration.getOrDefault("metadata", Map.of())))
            .set(TARGET_REGISTRATIONS.LAST_EVENT_ID, nullableText(registration.get("last_event_id")))
            .set(TARGET_REGISTRATIONS.CREATED_AT, createdAt)
            .set(TARGET_REGISTRATIONS.UPDATED_AT, now)
            .onConflict(TARGET_REGISTRATIONS.TARGET_ID)
            .doUpdate()
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, normalize(registration.get("display_name")))
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, nullableText(registration.get("managed_application_id")))
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, normalize(registration.get("managed_resource_group_id")))
            .set(TARGET_REGISTRATIONS.METADATA, toJson(registration.getOrDefault("metadata", Map.of())))
            .set(TARGET_REGISTRATIONS.LAST_EVENT_ID, nullableText(registration.get("last_event_id")))
            .set(TARGET_REGISTRATIONS.UPDATED_AT, now)
            .execute();
    }

    public void deleteRegistration(String targetId) {
        dsl.deleteFrom(TARGET_REGISTRATIONS)
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .execute();
    }

    @SuppressWarnings("unchecked")
    public void updateRegistrationAndTarget(String targetId, Map<String, Object> patch) {
        Map<String, Object> current = getRegistration(targetId);
        if (current.isEmpty()) {
            return;
        }

        String displayName = patch.containsKey("display_name")
            ? normalize(patch.get("display_name"))
            : normalize(current.get("display_name"));
        String managedApplicationId = patch.containsKey("managed_application_id")
            ? nullableText(patch.get("managed_application_id"))
            : nullableText(current.get("managed_application_id"));
        String managedResourceGroupId = patch.containsKey("managed_resource_group_id")
            ? normalize(patch.get("managed_resource_group_id"))
            : normalize(current.get("managed_resource_group_id"));

        Map<String, Object> metadata = new LinkedHashMap<>((Map<String, Object>) current.getOrDefault("metadata", Map.of()));
        if (patch.get("metadata") instanceof Map<?, ?> metadataPatch) {
            Map<String, Object> replaced = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : metadataPatch.entrySet()) {
                replaced.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            metadata = replaced;
        }

        dsl.update(TARGET_REGISTRATIONS)
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, displayName)
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, managedApplicationId)
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, managedResourceGroupId)
            .set(TARGET_REGISTRATIONS.METADATA, toJson(metadata))
            .set(TARGET_REGISTRATIONS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .execute();

        if (
            patch.containsKey("customer_name")
                || patch.containsKey("last_deployed_release")
                || patch.containsKey("health_status")
                || patch.containsKey("container_app_resource_id")
        ) {
            String managedAppId = patch.containsKey("container_app_resource_id")
                ? nullableText(patch.get("container_app_resource_id"))
                : nullableText(current.get("container_app_resource_id"));
            String customerName = patch.containsKey("customer_name")
                ? nullableText(patch.get("customer_name"))
                : nullableText(current.get("customer_name"));
            String lastDeployedRelease = patch.containsKey("last_deployed_release")
                ? normalize(patch.get("last_deployed_release"))
                : normalize(current.get("last_deployed_release"));
            String healthStatus = patch.containsKey("health_status")
                ? normalize(patch.get("health_status"))
                : normalize(current.get("health_status"));

            dsl.update(TARGETS)
                .set(TARGETS.MANAGED_APP_ID, managedAppId)
                .set(TARGETS.CUSTOMER_NAME, customerName)
                .set(
                    TARGETS.LAST_DEPLOYED_RELEASE,
                    lastDeployedRelease.isBlank() ? "unknown" : lastDeployedRelease
                )
                .set(
                    TARGETS.HEALTH_STATUS,
                    enumOrDefault(MappoHealthStatus.lookupLiteral(healthStatus), MappoHealthStatus.registered)
                )
                .set(TARGETS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(TARGETS.ID.eq(targetId))
                .execute();
        }

        if (patch.containsKey("tags") && patch.get("tags") instanceof Map<?, ?> tagPatch) {
            dsl.deleteFrom(TARGET_TAGS)
                .where(TARGET_TAGS.TARGET_ID.eq(targetId))
                .execute();

            for (Map.Entry<?, ?> entry : tagPatch.entrySet()) {
                String key = String.valueOf(entry.getKey()).trim();
                if (key.isEmpty()) {
                    continue;
                }
                dsl.insertInto(TARGET_TAGS)
                    .set(TARGET_TAGS.TARGET_ID, targetId)
                    .set(TARGET_TAGS.TAG_KEY, key)
                    .set(TARGET_TAGS.TAG_VALUE, String.valueOf(entry.getValue()))
                    .execute();
            }
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
            tags.computeIfAbsent(targetId, ignored -> new LinkedHashMap<>())
                .put(row.get(TARGET_TAGS.TAG_KEY), row.get(TARGET_TAGS.TAG_VALUE));
        }
        return tags;
    }

    private Map<String, Object> toRegistrationMap(Record row, Map<String, String> tags) {
        Map<String, Object> registration = new LinkedHashMap<>();
        registration.put("target_id", row.get(TARGET_REGISTRATIONS.TARGET_ID));
        registration.put("tenant_id", uuidText(row.get(TARGETS.TENANT_ID)));
        registration.put("subscription_id", uuidText(row.get(TARGETS.SUBSCRIPTION_ID)));
        registration.put("managed_application_id", row.get(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID));
        registration.put("managed_resource_group_id", row.get(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID));
        registration.put("container_app_resource_id", row.get(TARGETS.MANAGED_APP_ID));
        registration.put("display_name", row.get(TARGET_REGISTRATIONS.DISPLAY_NAME));
        registration.put("customer_name", row.get(TARGETS.CUSTOMER_NAME));
        registration.put("tags", tags);
        registration.put("metadata", parseJsonMap(row.get(TARGET_REGISTRATIONS.METADATA)));
        registration.put("last_event_id", row.get(TARGET_REGISTRATIONS.LAST_EVENT_ID));
        registration.put("last_deployed_release", row.get(TARGETS.LAST_DEPLOYED_RELEASE));
        registration.put("health_status", literal(row.get(TARGETS.HEALTH_STATUS)));
        registration.put("created_at", row.get(TARGET_REGISTRATIONS.CREATED_AT));
        registration.put("updated_at", row.get(TARGET_REGISTRATIONS.UPDATED_AT));
        return registration;
    }

    private JSONB toJson(Object value) {
        return JSONB.valueOf(jsonUtil.write(value));
    }

    private Map<String, Object> parseJsonMap(JSONB value) {
        if (value == null) {
            return Map.of();
        }
        return jsonUtil.readMap(value.data());
    }

    private String literal(org.jooq.EnumType value) {
        return value == null ? null : value.getLiteral();
    }

    private String uuidText(UUID value) {
        return value == null ? null : value.toString();
    }

    private UUID optionalUuid(Object value) {
        String text = normalize(value);
        if (text.isBlank()) {
            return null;
        }
        return UUID.fromString(text);
    }

    private UUID requiredUuid(Object value, String field) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(text);
    }

    private OffsetDateTime toTimestamp(Object value, OffsetDateTime fallback) {
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp;
        }
        return fallback;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Integer number) {
            return number;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = normalize(value);
        if (text.isBlank()) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        String text = normalize(value);
        return text.isBlank() ? null : text;
    }

    private <T> T enumOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
