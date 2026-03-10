package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.FORWARDER_LOGS;
import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGETS;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.model.command.ForwarderLogIngestCommand;
import com.mappo.controlplane.model.command.TargetRegistrationPatchCommand;
import com.mappo.controlplane.model.command.TargetRegistrationUpsertCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminCommandRepository {

    private final DSLContext dsl;
    private final AdminRepository adminRepository;

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
            .set(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME, nullableText(registration.deploymentStackName()))
            .set(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE, registration.registryAuthMode())
            .set(TARGET_REGISTRATIONS.REGISTRY_SERVER, nullableText(registration.registryServer()))
            .set(TARGET_REGISTRATIONS.REGISTRY_USERNAME, nullableText(registration.registryUsername()))
            .set(TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME, nullableText(registration.registryPasswordSecretName()))
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
            .set(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME, nullableText(registration.deploymentStackName()))
            .set(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE, registration.registryAuthMode())
            .set(TARGET_REGISTRATIONS.REGISTRY_SERVER, nullableText(registration.registryServer()))
            .set(TARGET_REGISTRATIONS.REGISTRY_USERNAME, nullableText(registration.registryUsername()))
            .set(TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME, nullableText(registration.registryPasswordSecretName()))
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
        Optional<TargetRegistrationRecord> currentOptional = adminRepository.getRegistration(targetId);
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
        String deploymentStackName = firstNullableText(
            patch.deploymentStackName(),
            current.metadata() == null ? null : current.metadata().deploymentStackName()
        );
        var registryAuthMode = patch.registryAuthMode() == null
            ? current.metadata() == null ? null : current.metadata().registryAuthMode()
            : patch.registryAuthMode();
        String registryServer = firstNullableText(
            patch.registryServer(),
            current.metadata() == null ? null : current.metadata().registryServer()
        );
        String registryUsername = firstNullableText(
            patch.registryUsername(),
            current.metadata() == null ? null : current.metadata().registryUsername()
        );
        String registryPasswordSecretName = firstNullableText(
            patch.registryPasswordSecretName(),
            current.metadata() == null ? null : current.metadata().registryPasswordSecretName()
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
            .set(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME, deploymentStackName)
            .set(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE, registryAuthMode)
            .set(TARGET_REGISTRATIONS.REGISTRY_SERVER, registryServer)
            .set(TARGET_REGISTRATIONS.REGISTRY_USERNAME, registryUsername)
            .set(TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME, registryPasswordSecretName)
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
    }

    private com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType toMarketplaceEventEnum(
        MarketplaceEventType type
    ) {
        return switch (enumOrDefault(type, MarketplaceEventType.SUBSCRIPTION_PURCHASED)) {
            case SUBSCRIPTION_PURCHASED -> com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.subscription_purchased;
            case SUBSCRIPTION_SUSPENDED -> com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.subscription_suspended;
            case SUBSCRIPTION_DELETED -> com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.subscription_deleted;
            case UNKNOWN -> com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.subscription_purchased;
        };
    }

    private String firstNullableText(String candidate, String fallback) {
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.isBlank() ? nullableText(fallback) : normalizedCandidate;
    }

    private String firstNonBlank(String candidate, String fallback) {
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.isBlank() ? normalize(fallback) : normalizedCandidate;
    }

    private String defaultIfBlank(String candidate, String fallback) {
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.isBlank() ? normalize(fallback) : normalizedCandidate;
    }

    private OffsetDateTime toTimestamp(OffsetDateTime value, OffsetDateTime fallback) {
        return value == null ? fallback : value;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private UUID requiredUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private <E extends Enum<E>> E enumOrDefault(E value, E fallback) {
        return value == null ? fallback : value;
    }
}
