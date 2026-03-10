package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.model.MarketplaceEventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MarketplaceEventCommandRepository {

    private final DSLContext dsl;

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
            .set(MARKETPLACE_EVENTS.ID, AdminCommandSupport.normalize(eventId))
            .set(
                MARKETPLACE_EVENTS.EVENT_TYPE,
                AdminCommandSupport.toMarketplaceEventEnum(
                    AdminCommandSupport.enumOrDefault(eventType, MarketplaceEventType.SUBSCRIPTION_PURCHASED)
                )
            )
            .set(MARKETPLACE_EVENTS.STATUS, AdminCommandSupport.enumOrDefault(status, MappoMarketplaceEventStatus.applied))
            .set(MARKETPLACE_EVENTS.MESSAGE, AdminCommandSupport.normalize(message))
            .set(MARKETPLACE_EVENTS.TARGET_ID, AdminCommandSupport.nullableText(targetId))
            .set(MARKETPLACE_EVENTS.TENANT_ID, AdminCommandSupport.requiredUuid(tenantId, "tenant_id"))
            .set(MARKETPLACE_EVENTS.SUBSCRIPTION_ID, AdminCommandSupport.requiredUuid(subscriptionId, "subscription_id"))
            .set(MARKETPLACE_EVENTS.DISPLAY_NAME, AdminCommandSupport.nullableText(displayName))
            .set(MARKETPLACE_EVENTS.CUSTOMER_NAME, AdminCommandSupport.nullableText(customerName))
            .set(MARKETPLACE_EVENTS.MANAGED_APPLICATION_ID, AdminCommandSupport.nullableText(managedApplicationId))
            .set(MARKETPLACE_EVENTS.MANAGED_RESOURCE_GROUP_ID, AdminCommandSupport.nullableText(managedResourceGroupId))
            .set(MARKETPLACE_EVENTS.CONTAINER_APP_RESOURCE_ID, AdminCommandSupport.nullableText(containerAppResourceId))
            .set(MARKETPLACE_EVENTS.CONTAINER_APP_NAME, AdminCommandSupport.nullableText(containerAppName))
            .set(MARKETPLACE_EVENTS.TARGET_GROUP, AdminCommandSupport.nullableText(targetGroup))
            .set(MARKETPLACE_EVENTS.REGION, AdminCommandSupport.nullableText(region))
            .set(MARKETPLACE_EVENTS.ENVIRONMENT, AdminCommandSupport.nullableText(environment))
            .set(MARKETPLACE_EVENTS.TIER, AdminCommandSupport.nullableText(tier))
            .set(MARKETPLACE_EVENTS.LAST_DEPLOYED_RELEASE, AdminCommandSupport.nullableText(lastDeployedRelease))
            .set(MARKETPLACE_EVENTS.HEALTH_STATUS, healthStatus)
            .set(MARKETPLACE_EVENTS.REGISTRATION_SOURCE, AdminCommandSupport.nullableText(registrationSource))
            .set(MARKETPLACE_EVENTS.MARKETPLACE_PAYLOAD_ID, AdminCommandSupport.nullableText(marketplacePayloadId))
            .set(MARKETPLACE_EVENTS.CREATED_AT, now)
            .set(MARKETPLACE_EVENTS.PROCESSED_AT, now)
            .execute();
    }
}
