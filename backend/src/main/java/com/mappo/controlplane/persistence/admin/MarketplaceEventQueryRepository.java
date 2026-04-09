package com.mappo.controlplane.persistence.admin;

import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;

import com.mappo.controlplane.model.MarketplaceEventPayloadRecord;
import com.mappo.controlplane.model.MarketplaceEventRecord;
import com.mappo.controlplane.model.MarketplaceEventType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MarketplaceEventQueryRepository {

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
}
