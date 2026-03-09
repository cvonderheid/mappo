package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RELEASE_WEBHOOK_DELIVERIES;

import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import com.mappo.controlplane.model.PageMetadataRecord;
import com.mappo.controlplane.model.ReleaseWebhookDeliveryPageRecord;
import com.mappo.controlplane.model.ReleaseWebhookDeliveryRecord;
import com.mappo.controlplane.model.command.ReleaseWebhookDeliveryCommand;
import com.mappo.controlplane.model.query.ReleaseWebhookDeliveryPageQuery;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReleaseWebhookRepository {

    private final DSLContext dsl;

    public List<ReleaseWebhookDeliveryRecord> listReleaseWebhookDeliveries(int limit) {
        return listReleaseWebhookDeliveriesPage(
            new ReleaseWebhookDeliveryPageQuery(0, Math.max(1, limit), null, null)
        ).items();
    }

    public ReleaseWebhookDeliveryPageRecord listReleaseWebhookDeliveriesPage(ReleaseWebhookDeliveryPageQuery query) {
        int page = normalizePage(query == null ? null : query.page());
        int size = normalizeSize(query == null ? null : query.size());
        Condition condition = buildDeliveryCondition(query);
        if (condition == null) {
            return new ReleaseWebhookDeliveryPageRecord(List.of(), new PageMetadataRecord(page, size, 0L, 0));
        }

        long totalItems = dsl.fetchCount(
            dsl.select(RELEASE_WEBHOOK_DELIVERIES.ID)
                .from(RELEASE_WEBHOOK_DELIVERIES)
                .where(condition)
        );
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

        var rows = dsl.select(
                RELEASE_WEBHOOK_DELIVERIES.ID,
                RELEASE_WEBHOOK_DELIVERIES.EXTERNAL_DELIVERY_ID,
                RELEASE_WEBHOOK_DELIVERIES.EVENT_TYPE,
                RELEASE_WEBHOOK_DELIVERIES.REPO,
                RELEASE_WEBHOOK_DELIVERIES.REF,
                RELEASE_WEBHOOK_DELIVERIES.MANIFEST_PATH,
                RELEASE_WEBHOOK_DELIVERIES.STATUS,
                RELEASE_WEBHOOK_DELIVERIES.MESSAGE,
                RELEASE_WEBHOOK_DELIVERIES.CHANGED_PATHS_TEXT,
                RELEASE_WEBHOOK_DELIVERIES.MANIFEST_RELEASE_COUNT,
                RELEASE_WEBHOOK_DELIVERIES.CREATED_COUNT,
                RELEASE_WEBHOOK_DELIVERIES.SKIPPED_COUNT,
                RELEASE_WEBHOOK_DELIVERIES.IGNORED_COUNT,
                RELEASE_WEBHOOK_DELIVERIES.CREATED_RELEASE_IDS_TEXT,
                RELEASE_WEBHOOK_DELIVERIES.RECEIVED_AT
            )
            .from(RELEASE_WEBHOOK_DELIVERIES)
            .where(condition)
            .orderBy(RELEASE_WEBHOOK_DELIVERIES.RECEIVED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch();

        return new ReleaseWebhookDeliveryPageRecord(
            rows.map(this::toRecord),
            new PageMetadataRecord(page, size, totalItems, totalPages)
        );
    }

    public void saveDelivery(ReleaseWebhookDeliveryCommand command) {
        OffsetDateTime receivedAt = command.receivedAt() == null
            ? OffsetDateTime.now(ZoneOffset.UTC)
            : command.receivedAt();

        dsl.insertInto(RELEASE_WEBHOOK_DELIVERIES)
            .set(RELEASE_WEBHOOK_DELIVERIES.ID, normalize(command.id()))
            .set(RELEASE_WEBHOOK_DELIVERIES.EXTERNAL_DELIVERY_ID, nullable(command.externalDeliveryId()))
            .set(RELEASE_WEBHOOK_DELIVERIES.EVENT_TYPE, normalize(command.eventType()))
            .set(RELEASE_WEBHOOK_DELIVERIES.REPO, nullable(command.repo()))
            .set(RELEASE_WEBHOOK_DELIVERIES.REF, nullable(command.ref()))
            .set(RELEASE_WEBHOOK_DELIVERIES.MANIFEST_PATH, nullable(command.manifestPath()))
            .set(RELEASE_WEBHOOK_DELIVERIES.STATUS, command.status())
            .set(RELEASE_WEBHOOK_DELIVERIES.MESSAGE, normalize(command.message()))
            .set(RELEASE_WEBHOOK_DELIVERIES.CHANGED_PATHS_TEXT, join(command.changedPaths()))
            .set(RELEASE_WEBHOOK_DELIVERIES.MANIFEST_RELEASE_COUNT, intOrZero(command.manifestReleaseCount()))
            .set(RELEASE_WEBHOOK_DELIVERIES.CREATED_COUNT, intOrZero(command.createdCount()))
            .set(RELEASE_WEBHOOK_DELIVERIES.SKIPPED_COUNT, intOrZero(command.skippedCount()))
            .set(RELEASE_WEBHOOK_DELIVERIES.IGNORED_COUNT, intOrZero(command.ignoredCount()))
            .set(RELEASE_WEBHOOK_DELIVERIES.CREATED_RELEASE_IDS_TEXT, join(command.createdReleaseIds()))
            .set(RELEASE_WEBHOOK_DELIVERIES.RECEIVED_AT, receivedAt)
            .execute();
    }

    private ReleaseWebhookDeliveryRecord toRecord(Record row) {
        return new ReleaseWebhookDeliveryRecord(
            row.get(RELEASE_WEBHOOK_DELIVERIES.ID),
            row.get(RELEASE_WEBHOOK_DELIVERIES.EXTERNAL_DELIVERY_ID),
            row.get(RELEASE_WEBHOOK_DELIVERIES.EVENT_TYPE),
            row.get(RELEASE_WEBHOOK_DELIVERIES.REPO),
            row.get(RELEASE_WEBHOOK_DELIVERIES.REF),
            row.get(RELEASE_WEBHOOK_DELIVERIES.MANIFEST_PATH),
            row.get(RELEASE_WEBHOOK_DELIVERIES.STATUS),
            row.get(RELEASE_WEBHOOK_DELIVERIES.MESSAGE),
            split(row.get(RELEASE_WEBHOOK_DELIVERIES.CHANGED_PATHS_TEXT)),
            row.get(RELEASE_WEBHOOK_DELIVERIES.MANIFEST_RELEASE_COUNT),
            row.get(RELEASE_WEBHOOK_DELIVERIES.CREATED_COUNT),
            row.get(RELEASE_WEBHOOK_DELIVERIES.SKIPPED_COUNT),
            row.get(RELEASE_WEBHOOK_DELIVERIES.IGNORED_COUNT),
            split(row.get(RELEASE_WEBHOOK_DELIVERIES.CREATED_RELEASE_IDS_TEXT)),
            row.get(RELEASE_WEBHOOK_DELIVERIES.RECEIVED_AT)
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullable(Object value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private Integer intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
            .map(this::normalize)
            .filter(value -> !value.isBlank())
            .reduce((left, right) -> left + "\n" + right)
            .orElse(null);
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .toList();
    }

    private Condition buildDeliveryCondition(ReleaseWebhookDeliveryPageQuery query) {
        if (query == null) {
            return DSL.trueCondition();
        }

        Condition condition = DSL.trueCondition();
        String deliveryId = normalize(query.deliveryId());
        String status = normalize(query.status()).toLowerCase();

        if (!deliveryId.isBlank()) {
            condition = condition.and(RELEASE_WEBHOOK_DELIVERIES.EXTERNAL_DELIVERY_ID.containsIgnoreCase(deliveryId));
        }
        if (!status.isBlank()) {
            MappoReleaseWebhookStatus parsedStatus = MappoReleaseWebhookStatus.lookupLiteral(status);
            if (parsedStatus == null) {
                return null;
            }
            condition = condition.and(RELEASE_WEBHOOK_DELIVERIES.STATUS.eq(parsedStatus));
        }
        return condition;
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
}
