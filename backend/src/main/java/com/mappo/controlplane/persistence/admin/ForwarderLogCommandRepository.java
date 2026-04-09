package com.mappo.controlplane.persistence.admin;
import com.mappo.controlplane.persistence.support.AdminCommandSupport;

import static com.mappo.controlplane.jooq.Tables.FORWARDER_LOGS;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.model.command.ForwarderLogIngestCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ForwarderLogCommandRepository {

    private final DSLContext dsl;

    public boolean forwarderLogExists(String logId) {
        return dsl.fetchExists(
            dsl.selectOne()
                .from(FORWARDER_LOGS)
                .where(FORWARDER_LOGS.ID.eq(logId))
        );
    }

    public void saveForwarderLog(ForwarderLogIngestCommand request) {
        dsl.insertInto(FORWARDER_LOGS)
            .set(FORWARDER_LOGS.ID, AdminCommandSupport.normalize(request.logId()))
            .set(FORWARDER_LOGS.LEVEL, AdminCommandSupport.enumOrDefault(request.level(), MappoForwarderLogLevel.error))
            .set(FORWARDER_LOGS.MESSAGE, AdminCommandSupport.normalize(request.message()))
            .set(FORWARDER_LOGS.EVENT_ID, AdminCommandSupport.nullableText(request.eventId()))
            .set(
                FORWARDER_LOGS.EVENT_TYPE,
                request.eventType() == null ? null : AdminCommandSupport.toMarketplaceEventEnum(request.eventType())
            )
            .set(FORWARDER_LOGS.TARGET_ID, AdminCommandSupport.nullableText(request.targetId()))
            .set(FORWARDER_LOGS.TENANT_ID, request.tenantId())
            .set(FORWARDER_LOGS.SUBSCRIPTION_ID, request.subscriptionId())
            .set(FORWARDER_LOGS.FUNCTION_APP_NAME, AdminCommandSupport.nullableText(request.functionAppName()))
            .set(FORWARDER_LOGS.FORWARDER_REQUEST_ID, AdminCommandSupport.nullableText(request.forwarderRequestId()))
            .set(FORWARDER_LOGS.BACKEND_STATUS_CODE, request.backendStatusCode())
            .set(FORWARDER_LOGS.DETAIL_TEXT, AdminCommandSupport.nullableText(request.detailText()))
            .set(FORWARDER_LOGS.BACKEND_RESPONSE_BODY, AdminCommandSupport.nullableText(request.backendResponseBody()))
            .set(
                FORWARDER_LOGS.CREATED_AT,
                AdminCommandSupport.toTimestamp(request.occurredAt(), OffsetDateTime.now(ZoneOffset.UTC))
            )
            .execute();
    }
}
