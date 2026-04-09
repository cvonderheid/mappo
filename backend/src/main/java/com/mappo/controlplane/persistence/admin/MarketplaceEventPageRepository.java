package com.mappo.controlplane.persistence.admin;

import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;

import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.model.MarketplaceEventPageRecord;
import com.mappo.controlplane.model.MarketplaceEventPayloadRecord;
import com.mappo.controlplane.model.MarketplaceEventRecord;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.PageMetadataRecord;
import com.mappo.controlplane.model.query.MarketplaceEventPageQuery;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MarketplaceEventPageRepository {

    private static final Field<String> PROJECT_ID =
        DSL.field(DSL.name("project_id"), SQLDataType.VARCHAR(128));

    private final DSLContext dsl;

    public MarketplaceEventPageRecord listMarketplaceEventsPage(MarketplaceEventPageQuery query) {
        int page = normalizePage(query == null ? null : query.page());
        int size = normalizeSize(query == null ? null : query.size());
        Condition condition = buildCondition(query);
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
                new MarketplaceEventPayloadRecord(
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
                ),
                row.get(MARKETPLACE_EVENTS.CREATED_AT),
                row.get(MARKETPLACE_EVENTS.PROCESSED_AT)
            ));
        }
        return new MarketplaceEventPageRecord(events, new PageMetadataRecord(page, size, totalItems, totalPages));
    }

    private Condition buildCondition(MarketplaceEventPageQuery query) {
        if (query == null) {
            return DSL.trueCondition();
        }

        Condition condition = DSL.trueCondition();
        String projectId = normalize(query.projectId());
        String eventId = normalize(query.eventId());
        MappoMarketplaceEventStatus status = query.status();

        if (!projectId.isBlank()) {
            condition = condition.and(PROJECT_ID.eq(projectId));
        }
        if (!eventId.isBlank()) {
            condition = condition.and(MARKETPLACE_EVENTS.ID.containsIgnoreCase(eventId));
        }
        if (status != null) {
            condition = condition.and(MARKETPLACE_EVENTS.STATUS.eq(status));
        }
        return condition;
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
