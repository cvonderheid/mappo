package com.mappo.controlplane.service.admin;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.OnboardingEventRequest;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.model.EventIngestResultRecord;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.repository.MarketplaceEventCommandRepository;
import com.mappo.controlplane.repository.TargetCommandRepository;
import com.mappo.controlplane.repository.TargetRegistrationCommandRepository;
import com.mappo.controlplane.repository.TargetRecordQueryRepository;
import com.mappo.controlplane.service.TransactionHookService;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketplaceOnboardingCommandService {

    private final MarketplaceEventCommandRepository marketplaceEventCommandRepository;
    private final TargetRecordQueryRepository targetRecordQueryRepository;
    private final TargetCommandRepository targetCommandRepository;
    private final TargetRegistrationCommandRepository targetRegistrationCommandRepository;
    private final MarketplaceOnboardingTargetFactory targetFactory;
    private final LiveUpdateService liveUpdateService;
    private final TransactionHookService transactionHookService;

    @Transactional
    public EventIngestResultRecord ingest(OnboardingEventRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "event request is required");
        }

        String eventId = normalize(request.eventId());
        MarketplaceEventType eventType = request.effectiveEventType();
        UUID tenantId = request.tenantId();
        UUID subscriptionId = request.subscriptionId();
        if (eventId.isBlank() || tenantId == null || subscriptionId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "eventId, tenantId, and subscriptionId are required");
        }
        if (marketplaceEventCommandRepository.marketplaceEventExists(eventId)) {
            return new EventIngestResultRecord(
                eventId,
                MappoMarketplaceEventStatus.duplicate,
                "event already processed",
                normalize(request.targetId())
            );
        }

        MarketplaceOnboardingTargetPlan targetPlan = targetFactory.create(request, eventId, OffsetDateTime.now(ZoneOffset.UTC));
        String targetId = targetPlan.targetId();
        String message = applyEvent(targetPlan, eventType, request, subscriptionId);
        marketplaceEventCommandRepository.saveMarketplaceEvent(
            eventId,
            eventType,
            MappoMarketplaceEventStatus.applied,
            message,
            targetId,
            tenantId,
            subscriptionId,
            nullable(request.displayName()),
            nullable(request.customerName()),
            nullable(request.managedApplicationId()),
            nullable(request.managedResourceGroupId()),
            nullable(request.containerAppResourceId()),
            nullable(request.containerAppName()),
            nullable(request.targetGroup()),
            nullable(request.region()),
            nullable(request.environment()),
            nullable(request.tier()),
            nullable(request.lastDeployedRelease()),
            request.healthStatus(),
            defaultIfBlank(request.registrationSource(), "manual"),
            request.marketplacePayloadId()
        );
        transactionHookService.afterCommitOrNow(() -> {
            liveUpdateService.emitAdminUpdated();
            liveUpdateService.emitTargetsUpdated(targetPlan.targetCommand().projectId());
        });
        return new EventIngestResultRecord(eventId, MappoMarketplaceEventStatus.applied, message, targetId);
    }

    private String applyEvent(
        MarketplaceOnboardingTargetPlan targetPlan,
        MarketplaceEventType eventType,
        OnboardingEventRequest request,
        UUID subscriptionId
    ) {
        String targetId = targetPlan.targetId();
        if (eventType.isDeleteLike()) {
            targetRegistrationCommandRepository.deleteRegistration(targetId);
            targetCommandRepository.deleteTarget(targetId);
            return "Deleted target registration and target.";
        }
        if (eventType.isSuspendLike()) {
            TargetRecord existing = targetRecordQueryRepository.getTarget(targetId).orElse(null);
            if (existing != null) {
                targetCommandRepository.updateTargetHealth(targetId, MappoHealthStatus.degraded);
                return "Marked target as degraded.";
            }
            return "Target not found; suspension acknowledged.";
        }

        String containerAppResourceId = normalize(request.containerAppResourceId());
        if (containerAppResourceId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "container_app_resource_id is required for registration events");
        }
        targetCommandRepository.upsertTarget(targetPlan.targetCommand());
        targetRegistrationCommandRepository.upsertRegistration(targetPlan.registrationCommand());

        return "Registered target " + targetId + " for subscription " + subscriptionId + ".";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
