package com.mappo.controlplane.persistence.admin;

import static com.mappo.controlplane.jooq.Tables.FORWARDER_LOGS;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.model.ForwarderLogDetailsRecord;
import com.mappo.controlplane.model.ForwarderLogPageRecord;
import com.mappo.controlplane.model.ForwarderLogRecord;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.PageMetadataRecord;
import com.mappo.controlplane.model.query.ForwarderLogPageQuery;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ForwarderLogPageRepository {

    private final DSLContext dsl;

    public ForwarderLogPageRecord listForwarderLogsPage(ForwarderLogPageQuery query) {
        int page = normalizePage(query == null ? null : query.page());
        int size = normalizeSize(query == null ? null : query.size());
        Condition condition = buildCondition(query);
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
                new ForwarderLogDetailsRecord(
                    nullableText(row.get(FORWARDER_LOGS.DETAIL_TEXT)),
                    nullableText(row.get(FORWARDER_LOGS.BACKEND_RESPONSE_BODY))
                ),
                row.get(FORWARDER_LOGS.CREATED_AT)
            ));
        }
        return new ForwarderLogPageRecord(logs, new PageMetadataRecord(page, size, totalItems, totalPages));
    }

    private Condition buildCondition(ForwarderLogPageQuery query) {
        if (query == null) {
            return DSL.trueCondition();
        }

        Condition condition = DSL.trueCondition();
        String logId = normalize(query.logId());
        MappoForwarderLogLevel level = query.level();

        if (!logId.isBlank()) {
            condition = condition.and(FORWARDER_LOGS.ID.containsIgnoreCase(logId));
        }
        if (level != null) {
            condition = condition.and(FORWARDER_LOGS.LEVEL.eq(level));
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
