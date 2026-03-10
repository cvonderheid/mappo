package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import com.mappo.controlplane.model.MarketplaceEventPayloadRecord;
import com.mappo.controlplane.model.MarketplaceEventRecord;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.TargetRegistrationMetadataRecord;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME,
                TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE,
                TARGET_REGISTRATIONS.REGISTRY_SERVER,
                TARGET_REGISTRATIONS.REGISTRY_USERNAME,
                TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME,
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
                TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME,
                TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE,
                TARGET_REGISTRATIONS.REGISTRY_SERVER,
                TARGET_REGISTRATIONS.REGISTRY_USERNAME,
                TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME,
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

    private TargetRegistrationMetadataRecord registrationMetadata(Record row) {
        return new TargetRegistrationMetadataRecord(
            nullableText(row.get(TARGET_REGISTRATIONS.CONTAINER_APP_NAME)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRATION_SOURCE)),
            nullableText(row.get(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME)),
            row.get(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_SERVER)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_USERNAME)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME))
        );
    }

    private MarketplaceEventType toMarketplaceEventType(
        com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType value
    ) {
        if (value == null) {
            return null;
        }
        return MarketplaceEventType.fromValue(value.getLiteral());
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
