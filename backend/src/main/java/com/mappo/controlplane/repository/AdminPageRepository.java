package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.FORWARDER_LOGS;
import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.model.ForwarderLogDetailsRecord;
import com.mappo.controlplane.model.ForwarderLogPageRecord;
import com.mappo.controlplane.model.ForwarderLogRecord;
import com.mappo.controlplane.model.MarketplaceEventPageRecord;
import com.mappo.controlplane.model.MarketplaceEventPayloadRecord;
import com.mappo.controlplane.model.MarketplaceEventRecord;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.PageMetadataRecord;
import com.mappo.controlplane.model.TargetRegistrationMetadataRecord;
import com.mappo.controlplane.model.TargetRegistrationPageRecord;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.model.query.ForwarderLogPageQuery;
import com.mappo.controlplane.model.query.MarketplaceEventPageQuery;
import com.mappo.controlplane.model.query.TargetRegistrationPageQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminPageRepository {

    private final DSLContext dsl;

    public MarketplaceEventPageRecord listMarketplaceEventsPage(MarketplaceEventPageQuery query) {
        int page = normalizePage(query == null ? null : query.page());
        int size = normalizeSize(query == null ? null : query.size());
        Condition condition = buildMarketplaceEventCondition(query);
        if (condition == null) {
            return new MarketplaceEventPageRecord(List.of(), new PageMetadataRecord(page, size, 0L, 0));
        }

        long totalItems = dsl.fetchCount(
            dsl.select(MARKETPLACE_EVENTS.ID)
                .from(MARKETPLACE_EVENTS)
                .where(condition)
        );
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

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
            .where(condition)
            .orderBy(MARKETPLACE_EVENTS.CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
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
        return new MarketplaceEventPageRecord(events, new PageMetadataRecord(page, size, totalItems, totalPages));
    }

    public ForwarderLogPageRecord listForwarderLogsPage(ForwarderLogPageQuery query) {
        int page = normalizePage(query == null ? null : query.page());
        int size = normalizeSize(query == null ? null : query.size());
        Condition condition = buildForwarderLogCondition(query);
        if (condition == null) {
            return new ForwarderLogPageRecord(List.of(), new PageMetadataRecord(page, size, 0L, 0));
        }

        long totalItems = dsl.fetchCount(
            dsl.select(FORWARDER_LOGS.ID)
                .from(FORWARDER_LOGS)
                .where(condition)
        );
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

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
            .where(condition)
            .orderBy(FORWARDER_LOGS.CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
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
        return new ForwarderLogPageRecord(logs, new PageMetadataRecord(page, size, totalItems, totalPages));
    }

    public TargetRegistrationPageRecord listRegistrationsPage(TargetRegistrationPageQuery query) {
        int page = normalizePage(query == null ? null : query.page());
        int size = normalizeSize(query == null ? null : query.size());
        Condition condition = buildRegistrationCondition(query);

        long totalItems = dsl.fetchCount(
            dsl.select(TARGET_REGISTRATIONS.TARGET_ID)
                .from(TARGET_REGISTRATIONS)
                .join(TARGETS)
                .on(TARGETS.ID.eq(TARGET_REGISTRATIONS.TARGET_ID))
                .where(condition)
        );
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

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
            .where(condition)
            .orderBy(TARGET_REGISTRATIONS.UPDATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch();

        List<String> targetIds = rows.stream().map(row -> row.get(TARGET_REGISTRATIONS.TARGET_ID)).toList();
        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);

        List<TargetRegistrationRecord> registrations = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGET_REGISTRATIONS.TARGET_ID);
            registrations.add(toRegistrationRecord(row, tagsByTarget.getOrDefault(targetId, Map.of())));
        }
        return new TargetRegistrationPageRecord(
            registrations,
            new PageMetadataRecord(page, size, totalItems, totalPages)
        );
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
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRATION_SOURCE)),
            nullableText(row.get(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME)),
            row.get(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_SERVER)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_USERNAME)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME))
        );
    }

    private Condition buildRegistrationCondition(TargetRegistrationPageQuery query) {
        if (query == null) {
            return DSL.trueCondition();
        }

        Condition condition = DSL.trueCondition();
        String targetId = normalize(query.targetId());
        String ring = normalize(query.ring());
        String region = normalize(query.region());
        String tier = normalize(query.tier());

        if (!targetId.isBlank()) {
            condition = condition.and(TARGET_REGISTRATIONS.TARGET_ID.containsIgnoreCase(targetId));
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
        return condition;
    }

    private Condition buildMarketplaceEventCondition(MarketplaceEventPageQuery query) {
        if (query == null) {
            return DSL.trueCondition();
        }

        Condition condition = DSL.trueCondition();
        String eventId = normalize(query.eventId());
        String status = normalize(query.status()).toLowerCase();

        if (!eventId.isBlank()) {
            condition = condition.and(MARKETPLACE_EVENTS.ID.containsIgnoreCase(eventId));
        }
        if (!status.isBlank()) {
            MappoMarketplaceEventStatus parsedStatus = MappoMarketplaceEventStatus.lookupLiteral(status);
            if (parsedStatus == null) {
                return null;
            }
            condition = condition.and(MARKETPLACE_EVENTS.STATUS.eq(parsedStatus));
        }
        return condition;
    }

    private Condition buildForwarderLogCondition(ForwarderLogPageQuery query) {
        if (query == null) {
            return DSL.trueCondition();
        }

        Condition condition = DSL.trueCondition();
        String logId = normalize(query.logId());
        String level = normalize(query.level()).toLowerCase();

        if (!logId.isBlank()) {
            condition = condition.and(FORWARDER_LOGS.ID.containsIgnoreCase(logId));
        }
        if (!level.isBlank()) {
            MappoForwarderLogLevel parsedLevel = MappoForwarderLogLevel.lookupLiteral(level);
            if (parsedLevel == null) {
                return null;
            }
            condition = condition.and(FORWARDER_LOGS.LEVEL.eq(parsedLevel));
        }
        return condition;
    }

    private Condition tagEqualsCondition(String key, String value) {
        var tagFilterTable = TARGET_TAGS.as("tt_" + key);
        return DSL.exists(
            DSL.selectOne()
                .from(tagFilterTable)
                .where(tagFilterTable.TARGET_ID.eq(TARGET_REGISTRATIONS.TARGET_ID))
                .and(tagFilterTable.TAG_KEY.eq(key))
                .and(tagFilterTable.TAG_VALUE.eq(value))
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

    private int normalizePage(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private int normalizeSize(Integer value) {
        if (value == null || value <= 0) {
            return 25;
        }
        return Math.min(value, 100);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        String text = normalize(value);
        return text.isBlank() ? null : text;
    }
}
