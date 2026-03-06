package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.FORWARDER_LOGS;
import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.model.ForwarderLogDetailsRecord;
import com.mappo.controlplane.model.ForwarderLogRecord;
import com.mappo.controlplane.model.MarketplaceEventPayloadRecord;
import com.mappo.controlplane.model.MarketplaceEventRecord;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.model.TargetRegistrationMetadataRecord;
import com.mappo.controlplane.model.command.ForwarderLogIngestCommand;
import com.mappo.controlplane.model.command.TargetRegistrationPatchCommand;
import com.mappo.controlplane.model.command.TargetRegistrationUpsertCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminRepository {

    private final DSLContext dsl;

    public List<MarketplaceEventRecord> listMarketplaceEvents(int limit) {
        var rows = dsl.select(
                MARKETPLACE_EVENTS.ID,
                MARKETPLACE_EVENTS.EVENT_TYPE,
                MARKETPLACE_EVENTS.STATUS,
                MARKETPLACE_EVENTS.MESSAGE,
                MARKETPLACE_EVENTS.TARGET_ID,
                MARKETPLACE_EVENTS.TENANT_ID,
                MARKETPLACE_EVENTS.SUBSCRIPTION_ID,
                MARKETPLACE_EVENTS.DISPLAY_NAME,
                MARKETPLACE_EVENTS.CUSTOMER_NAME,
                MARKETPLACE_EVENTS.MANAGED_APPLICATION_ID,
                MARKETPLACE_EVENTS.MANAGED_RESOURCE_GROUP_ID,
                MARKETPLACE_EVENTS.CONTAINER_APP_RESOURCE_ID,
                MARKETPLACE_EVENTS.CONTAINER_APP_NAME,
                MARKETPLACE_EVENTS.TARGET_GROUP,
                MARKETPLACE_EVENTS.REGION,
                MARKETPLACE_EVENTS.ENVIRONMENT,
                MARKETPLACE_EVENTS.TIER,
                MARKETPLACE_EVENTS.LAST_DEPLOYED_RELEASE,
                MARKETPLACE_EVENTS.HEALTH_STATUS,
                MARKETPLACE_EVENTS.REGISTRATION_SOURCE,
                MARKETPLACE_EVENTS.MARKETPLACE_PAYLOAD_ID,
                MARKETPLACE_EVENTS.CREATED_AT,
                MARKETPLACE_EVENTS.PROCESSED_AT
            )
            .from(MARKETPLACE_EVENTS)
            .orderBy(MARKETPLACE_EVENTS.CREATED_AT.desc())
            .limit(Math.max(1, limit))
            .fetch();

        List<MarketplaceEventRecord> events = new ArrayList<>(rows.size());
        for (Record row : rows) {
            events.add(new MarketplaceEventRecord(
                row.get(MARKETPLACE_EVENTS.ID),
                toMarketplaceEventType(row.get(MARKETPLACE_EVENTS.EVENT_TYPE)),
                row.get(MARKETPLACE_EVENTS.STATUS),
                row.get(MARKETPLACE_EVENTS.MESSAGE),
                row.get(MARKETPLACE_EVENTS.TARGET_ID),
                row.get(MARKETPLACE_EVENTS.TENANT_ID),
                row.get(MARKETPLACE_EVENTS.SUBSCRIPTION_ID),
                eventPayload(row),
                row.get(MARKETPLACE_EVENTS.CREATED_AT),
                row.get(MARKETPLACE_EVENTS.PROCESSED_AT)
            ));
        }
        return events;
    }

    public List<ForwarderLogRecord> listForwarderLogs(int limit) {
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
                FORWARDER_LOGS.DETAIL_TEXT,
                FORWARDER_LOGS.BACKEND_RESPONSE_BODY,
                FORWARDER_LOGS.CREATED_AT
            )
            .from(FORWARDER_LOGS)
            .orderBy(FORWARDER_LOGS.CREATED_AT.desc())
            .limit(Math.max(1, limit))
            .fetch();

        List<ForwarderLogRecord> logs = new ArrayList<>(rows.size());
        for (Record row : rows) {
            logs.add(new ForwarderLogRecord(
                row.get(FORWARDER_LOGS.ID),
                row.get(FORWARDER_LOGS.LEVEL),
                row.get(FORWARDER_LOGS.MESSAGE),
                row.get(FORWARDER_LOGS.EVENT_ID),
                toMarketplaceEventType(row.get(FORWARDER_LOGS.EVENT_TYPE)),
                row.get(FORWARDER_LOGS.TARGET_ID),
                row.get(FORWARDER_LOGS.TENANT_ID),
                row.get(FORWARDER_LOGS.SUBSCRIPTION_ID),
                row.get(FORWARDER_LOGS.FUNCTION_APP_NAME),
                row.get(FORWARDER_LOGS.FORWARDER_REQUEST_ID),
                row.get(FORWARDER_LOGS.BACKEND_STATUS_CODE),
                forwarderDetails(row),
                row.get(FORWARDER_LOGS.CREATED_AT)
            ));
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
        MarketplaceEventType eventType,
        MappoMarketplaceEventStatus status,
        String message,
        String targetId,
        UUID tenantId,
        UUID subscriptionId,
        String displayName,
        String customerName,
        String managedApplicationId,
        String managedResourceGroupId,
        String containerAppResourceId,
        String containerAppName,
        String targetGroup,
        String region,
        String environment,
        String tier,
        String lastDeployedRelease,
        MappoHealthStatus healthStatus,
        String registrationSource,
        String marketplacePayloadId
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(MARKETPLACE_EVENTS)
            .set(MARKETPLACE_EVENTS.ID, eventId)
            .set(
                MARKETPLACE_EVENTS.EVENT_TYPE,
                toMarketplaceEventEnum(enumOrDefault(eventType, MarketplaceEventType.SUBSCRIPTION_PURCHASED))
            )
            .set(MARKETPLACE_EVENTS.STATUS, enumOrDefault(status, MappoMarketplaceEventStatus.applied))
            .set(MARKETPLACE_EVENTS.MESSAGE, normalize(message))
            .set(MARKETPLACE_EVENTS.TARGET_ID, nullableText(targetId))
            .set(MARKETPLACE_EVENTS.TENANT_ID, requiredUuid(tenantId, "tenant_id"))
            .set(MARKETPLACE_EVENTS.SUBSCRIPTION_ID, requiredUuid(subscriptionId, "subscription_id"))
            .set(MARKETPLACE_EVENTS.DISPLAY_NAME, nullableText(displayName))
            .set(MARKETPLACE_EVENTS.CUSTOMER_NAME, nullableText(customerName))
            .set(MARKETPLACE_EVENTS.MANAGED_APPLICATION_ID, nullableText(managedApplicationId))
            .set(MARKETPLACE_EVENTS.MANAGED_RESOURCE_GROUP_ID, nullableText(managedResourceGroupId))
            .set(MARKETPLACE_EVENTS.CONTAINER_APP_RESOURCE_ID, nullableText(containerAppResourceId))
            .set(MARKETPLACE_EVENTS.CONTAINER_APP_NAME, nullableText(containerAppName))
            .set(MARKETPLACE_EVENTS.TARGET_GROUP, nullableText(targetGroup))
            .set(MARKETPLACE_EVENTS.REGION, nullableText(region))
            .set(MARKETPLACE_EVENTS.ENVIRONMENT, nullableText(environment))
            .set(MARKETPLACE_EVENTS.TIER, nullableText(tier))
            .set(MARKETPLACE_EVENTS.LAST_DEPLOYED_RELEASE, nullableText(lastDeployedRelease))
            .set(MARKETPLACE_EVENTS.HEALTH_STATUS, healthStatus)
            .set(MARKETPLACE_EVENTS.REGISTRATION_SOURCE, nullableText(registrationSource))
            .set(MARKETPLACE_EVENTS.MARKETPLACE_PAYLOAD_ID, nullableText(marketplacePayloadId))
            .set(MARKETPLACE_EVENTS.CREATED_AT, now)
            .set(MARKETPLACE_EVENTS.PROCESSED_AT, now)
            .execute();
    }

    public boolean forwarderLogExists(String logId) {
        return dsl.fetchExists(
            dsl.selectOne()
                .from(FORWARDER_LOGS)
                .where(FORWARDER_LOGS.ID.eq(logId))
        );
    }

    public void saveForwarderLog(ForwarderLogIngestCommand request) {
        dsl.insertInto(FORWARDER_LOGS)
            .set(FORWARDER_LOGS.ID, normalize(request.logId()))
            .set(FORWARDER_LOGS.LEVEL, enumOrDefault(request.level(), MappoForwarderLogLevel.error))
            .set(FORWARDER_LOGS.MESSAGE, normalize(request.message()))
            .set(FORWARDER_LOGS.EVENT_ID, nullableText(request.eventId()))
            .set(
                FORWARDER_LOGS.EVENT_TYPE,
                request.eventType() == null ? null : toMarketplaceEventEnum(request.eventType())
            )
            .set(FORWARDER_LOGS.TARGET_ID, nullableText(request.targetId()))
            .set(FORWARDER_LOGS.TENANT_ID, request.tenantId())
            .set(FORWARDER_LOGS.SUBSCRIPTION_ID, request.subscriptionId())
            .set(FORWARDER_LOGS.FUNCTION_APP_NAME, nullableText(request.functionAppName()))
            .set(FORWARDER_LOGS.FORWARDER_REQUEST_ID, nullableText(request.forwarderRequestId()))
            .set(FORWARDER_LOGS.BACKEND_STATUS_CODE, request.backendStatusCode())
            .set(FORWARDER_LOGS.DETAIL_TEXT, nullableText(request.detailText()))
            .set(FORWARDER_LOGS.BACKEND_RESPONSE_BODY, nullableText(request.backendResponseBody()))
            .set(FORWARDER_LOGS.CREATED_AT, toTimestamp(request.occurredAt(), OffsetDateTime.now(ZoneOffset.UTC)))
            .execute();
    }

    public List<TargetRegistrationRecord> listRegistrations() {
        var rows = dsl.select(
                TARGET_REGISTRATIONS.TARGET_ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID,
                TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.DISPLAY_NAME,
                TARGET_REGISTRATIONS.CUSTOMER_NAME,
                TARGET_REGISTRATIONS.CONTAINER_APP_NAME,
                TARGET_REGISTRATIONS.REGISTRATION_SOURCE,
                TARGET_REGISTRATIONS.LAST_EVENT_ID,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGET_REGISTRATIONS.CREATED_AT,
                TARGET_REGISTRATIONS.UPDATED_AT
            )
            .from(TARGET_REGISTRATIONS)
            .join(TARGETS)
            .on(TARGETS.ID.eq(TARGET_REGISTRATIONS.TARGET_ID))
            .orderBy(TARGET_REGISTRATIONS.UPDATED_AT.desc())
            .fetch();

        List<String> targetIds = rows.stream().map(row -> row.get(TARGET_REGISTRATIONS.TARGET_ID)).toList();
        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);

        List<TargetRegistrationRecord> registrations = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGET_REGISTRATIONS.TARGET_ID);
            registrations.add(toRegistrationRecord(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }
        return registrations;
    }

    public Optional<TargetRegistrationRecord> getRegistration(String targetId) {
        Record row = dsl.select(
                TARGET_REGISTRATIONS.TARGET_ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID,
                TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.DISPLAY_NAME,
                TARGET_REGISTRATIONS.CUSTOMER_NAME,
                TARGET_REGISTRATIONS.CONTAINER_APP_NAME,
                TARGET_REGISTRATIONS.REGISTRATION_SOURCE,
                TARGET_REGISTRATIONS.LAST_EVENT_ID,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGET_REGISTRATIONS.CREATED_AT,
                TARGET_REGISTRATIONS.UPDATED_AT
            )
            .from(TARGET_REGISTRATIONS)
            .join(TARGETS)
            .on(TARGETS.ID.eq(TARGET_REGISTRATIONS.TARGET_ID))
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .fetchOne();

        if (row == null) {
            return Optional.empty();
        }

        Map<String, String> tags = loadTags(List.of(targetId)).getOrDefault(targetId, Map.of());
        return Optional.of(toRegistrationRecord(row, tags));
    }

    public void upsertRegistration(TargetRegistrationUpsertCommand registration) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime createdAt = toTimestamp(registration.createdAt(), now);

        dsl.insertInto(TARGET_REGISTRATIONS)
            .set(TARGET_REGISTRATIONS.TARGET_ID, normalize(registration.targetId()))
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, normalize(registration.displayName()))
            .set(TARGET_REGISTRATIONS.CUSTOMER_NAME, nullableText(registration.customerName()))
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, nullableText(registration.managedApplicationId()))
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, normalize(registration.managedResourceGroupId()))
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID, normalize(registration.containerAppResourceId()))
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_NAME, nullableText(registration.containerAppName()))
            .set(TARGET_REGISTRATIONS.REGISTRATION_SOURCE, nullableText(registration.registrationSource()))
            .set(TARGET_REGISTRATIONS.LAST_EVENT_ID, nullableText(registration.lastEventId()))
            .set(TARGET_REGISTRATIONS.CREATED_AT, createdAt)
            .set(TARGET_REGISTRATIONS.UPDATED_AT, now)
            .onConflict(TARGET_REGISTRATIONS.TARGET_ID)
            .doUpdate()
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, normalize(registration.displayName()))
            .set(TARGET_REGISTRATIONS.CUSTOMER_NAME, nullableText(registration.customerName()))
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, nullableText(registration.managedApplicationId()))
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, normalize(registration.managedResourceGroupId()))
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID, normalize(registration.containerAppResourceId()))
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_NAME, nullableText(registration.containerAppName()))
            .set(TARGET_REGISTRATIONS.REGISTRATION_SOURCE, nullableText(registration.registrationSource()))
            .set(TARGET_REGISTRATIONS.LAST_EVENT_ID, nullableText(registration.lastEventId()))
            .set(TARGET_REGISTRATIONS.UPDATED_AT, now)
            .execute();
    }

    public void deleteRegistration(String targetId) {
        dsl.deleteFrom(TARGET_REGISTRATIONS)
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .execute();
    }

    public void updateRegistrationAndTarget(String targetId, TargetRegistrationPatchCommand patch) {
        Optional<TargetRegistrationRecord> currentOptional = getRegistration(targetId);
        if (currentOptional.isEmpty()) {
            return;
        }
        TargetRegistrationRecord current = currentOptional.get();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String displayName = firstNonBlank(patch.displayName(), current.displayName());
        String customerName = firstNullableText(patch.customerName(), current.customerName());
        String managedApplicationId = firstNullableText(
            patch.managedApplicationId(),
            current.managedApplicationId()
        );
        String managedResourceGroupId = firstNonBlank(
            patch.managedResourceGroupId(),
            current.managedResourceGroupId()
        );
        String containerAppResourceId = firstNonBlank(
            patch.containerAppResourceId(),
            current.containerAppResourceId()
        );
        String containerAppName = firstNullableText(
            patch.containerAppName(),
            current.metadata() == null ? null : current.metadata().containerAppName()
        );
        String registrationSource = firstNullableText(
            patch.registrationSource(),
            current.metadata() == null ? null : current.metadata().source()
        );

        dsl.update(TARGET_REGISTRATIONS)
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, displayName)
            .set(TARGET_REGISTRATIONS.CUSTOMER_NAME, customerName)
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, managedApplicationId)
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, managedResourceGroupId)
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID, containerAppResourceId)
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_NAME, containerAppName)
            .set(TARGET_REGISTRATIONS.REGISTRATION_SOURCE, registrationSource)
            .set(TARGET_REGISTRATIONS.UPDATED_AT, now)
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .execute();

        if (patch.lastDeployedRelease() != null || patch.healthStatus() != null) {
            String lastDeployedRelease = firstNonBlank(
                patch.lastDeployedRelease(),
                current.lastDeployedRelease()
            );
            dsl.update(TARGETS)
                .set(TARGETS.LAST_DEPLOYED_RELEASE, defaultIfBlank(lastDeployedRelease, "unknown"))
                .set(
                    TARGETS.HEALTH_STATUS,
                    patch.healthStatus() == null
                        ? enumOrDefault(current.healthStatus(), MappoHealthStatus.registered)
                        : enumOrDefault(patch.healthStatus(), MappoHealthStatus.registered)
                )
                .set(TARGETS.UPDATED_AT, now)
                .where(TARGETS.ID.eq(targetId))
                .execute();
        }

        if (patch.tags() != null) {
            dsl.deleteFrom(TARGET_TAGS)
                .where(TARGET_TAGS.TARGET_ID.eq(targetId))
                .execute();

            for (Map.Entry<String, String> entry : patch.tags().entrySet()) {
                String key = normalize(entry.getKey());
                if (key.isEmpty()) {
                    continue;
                }
                dsl.insertInto(TARGET_TAGS)
                    .set(TARGET_TAGS.TARGET_ID, targetId)
                    .set(TARGET_TAGS.TAG_KEY, key)
                    .set(TARGET_TAGS.TAG_VALUE, normalize(entry.getValue()))
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

    private TargetRegistrationRecord toRegistrationRecord(Record row, Map<String, String> tags) {
        return new TargetRegistrationRecord(
            row.get(TARGET_REGISTRATIONS.TARGET_ID),
            row.get(TARGETS.TENANT_ID),
            row.get(TARGETS.SUBSCRIPTION_ID),
            row.get(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID),
            row.get(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID),
            row.get(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID),
            row.get(TARGET_REGISTRATIONS.DISPLAY_NAME),
            row.get(TARGET_REGISTRATIONS.CUSTOMER_NAME),
            tags,
            registrationMetadata(row),
            row.get(TARGET_REGISTRATIONS.LAST_EVENT_ID),
            row.get(TARGETS.LAST_DEPLOYED_RELEASE),
            row.get(TARGETS.HEALTH_STATUS),
            row.get(TARGET_REGISTRATIONS.CREATED_AT),
            row.get(TARGET_REGISTRATIONS.UPDATED_AT)
        );
    }

    private MarketplaceEventPayloadRecord eventPayload(Record row) {
        return new MarketplaceEventPayloadRecord(
            nullableText(row.get(MARKETPLACE_EVENTS.DISPLAY_NAME)),
            nullableText(row.get(MARKETPLACE_EVENTS.CUSTOMER_NAME)),
            nullableText(row.get(MARKETPLACE_EVENTS.MANAGED_APPLICATION_ID)),
            nullableText(row.get(MARKETPLACE_EVENTS.MANAGED_RESOURCE_GROUP_ID)),
            nullableText(row.get(MARKETPLACE_EVENTS.CONTAINER_APP_RESOURCE_ID)),
            nullableText(row.get(MARKETPLACE_EVENTS.CONTAINER_APP_NAME)),
            nullableText(row.get(MARKETPLACE_EVENTS.TARGET_GROUP)),
            nullableText(row.get(MARKETPLACE_EVENTS.REGION)),
            nullableText(row.get(MARKETPLACE_EVENTS.ENVIRONMENT)),
            nullableText(row.get(MARKETPLACE_EVENTS.TIER)),
            nullableText(row.get(MARKETPLACE_EVENTS.LAST_DEPLOYED_RELEASE)),
            row.get(MARKETPLACE_EVENTS.HEALTH_STATUS),
            nullableText(row.get(MARKETPLACE_EVENTS.REGISTRATION_SOURCE)),
            nullableText(row.get(MARKETPLACE_EVENTS.MARKETPLACE_PAYLOAD_ID))
        );
    }

    private ForwarderLogDetailsRecord forwarderDetails(Record row) {
        return new ForwarderLogDetailsRecord(
            nullableText(row.get(FORWARDER_LOGS.DETAIL_TEXT)),
            nullableText(row.get(FORWARDER_LOGS.BACKEND_RESPONSE_BODY))
        );
    }

    private TargetRegistrationMetadataRecord registrationMetadata(Record row) {
        return new TargetRegistrationMetadataRecord(
            nullableText(row.get(TARGET_REGISTRATIONS.CONTAINER_APP_NAME)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRATION_SOURCE))
        );
    }

    private OffsetDateTime toTimestamp(Object value, OffsetDateTime fallback) {
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp;
        }
        return fallback;
    }

    private UUID requiredUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private MarketplaceEventType toMarketplaceEventType(
        com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType value
    ) {
        if (value == null) {
            return null;
        }
        return MarketplaceEventType.fromValue(value.getLiteral());
    }

    private com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType toMarketplaceEventEnum(
        MarketplaceEventType value
    ) {
        return com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.lookupLiteral(value.literal());
    }

    private <T> T enumOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        String text = normalize(value);
        return text.isBlank() ? null : text;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalizedPreferred = normalize(preferred);
        return normalizedPreferred.isBlank() ? normalize(fallback) : normalizedPreferred;
    }

    private String firstNullableText(String preferred, String fallback) {
        String normalizedPreferred = nullableText(preferred);
        return normalizedPreferred != null ? normalizedPreferred : nullableText(fallback);
    }
}
