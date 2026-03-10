package com.mappo.controlplane.service.admin;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ForwarderLogIngestRequest;
import com.mappo.controlplane.api.request.OnboardingEventRequest;
import com.mappo.controlplane.api.request.TargetRegistrationPatchRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.model.EventIngestResultRecord;
import com.mappo.controlplane.model.ForwarderLogIngestResultRecord;
import com.mappo.controlplane.model.MarketplaceEventType;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.model.command.TargetRegistrationUpsertCommand;
import com.mappo.controlplane.model.command.TargetUpsertCommand;
import com.mappo.controlplane.repository.AdminCommandRepository;
import com.mappo.controlplane.repository.AdminRepository;
import com.mappo.controlplane.repository.TargetCommandRepository;
import com.mappo.controlplane.repository.TargetQueryRepository;
import com.mappo.controlplane.service.TransactionHookService;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCommandService {

    private final AdminRepository adminRepository;
    private final AdminCommandRepository adminCommandRepository;
    private final TargetQueryRepository targetQueryRepository;
    private final TargetCommandRepository targetCommandRepository;
    private final MappoProperties properties;
    private final LiveUpdateService liveUpdateService;
    private final TransactionHookService transactionHookService;

    @Transactional
    public EventIngestResultRecord ingestMarketplaceEvent(OnboardingEventRequest request) {
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

        if (adminCommandRepository.marketplaceEventExists(eventId)) {
            return new EventIngestResultRecord(
                eventId,
                MappoMarketplaceEventStatus.duplicate,
                "event already processed",
                normalize(request.targetId())
            );
        }

        String targetId = resolveTargetId(request);
        String message;

        if (eventType.isDeleteLike()) {
            adminCommandRepository.deleteRegistration(targetId);
            targetCommandRepository.deleteTarget(targetId);
            message = "Deleted target registration and target.";
        } else if (eventType.isSuspendLike()) {
            TargetRecord existing = targetQueryRepository.getTarget(targetId).orElse(null);
            if (existing != null) {
                targetCommandRepository.updateTargetHealth(targetId, MappoHealthStatus.degraded);
                message = "Marked target as degraded.";
            } else {
                message = "Target not found; suspension acknowledged.";
            }
        } else {
            String containerAppResourceId = normalize(request.containerAppResourceId());
            if (containerAppResourceId.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "container_app_resource_id is required for registration events");
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Map<String, String> tags = buildTags(request);
            TargetUpsertCommand target = new TargetUpsertCommand(
                targetId,
                tenantId,
                subscriptionId,
                tags,
                defaultIfBlank(normalize(request.lastDeployedRelease()), "unknown"),
                request.healthStatus() == null ? MappoHealthStatus.registered : request.healthStatus(),
                now,
                MappoSimulatedFailureMode.none
            );
            targetCommandRepository.upsertTarget(target);

            String managedResourceGroupId = normalize(request.managedResourceGroupId());
            if (managedResourceGroupId.isBlank()) {
                managedResourceGroupId = deriveResourceGroupIdFromContainerApp(containerAppResourceId);
            }

            TargetRegistrationUpsertCommand registration = new TargetRegistrationUpsertCommand(
                targetId,
                defaultIfBlank(normalize(request.displayName()), targetId),
                nullable(request.customerName()),
                nullable(request.managedApplicationId()),
                managedResourceGroupId,
                containerAppResourceId,
                nullable(request.containerAppName()),
                defaultIfBlank(request.registrationSource(), "manual"),
                defaultDeploymentStackName(targetId),
                defaultRegistryAuthMode(),
                nullable(properties.getPublisherAcr().getServer()),
                nullable(properties.getPublisherAcr().getPullClientId()),
                nullable(properties.getPublisherAcr().getPullSecretName()),
                eventId,
                now
            );
            adminCommandRepository.upsertRegistration(registration);

            message = "Registered target " + targetId + " for subscription " + subscriptionId + ".";
        }

        adminCommandRepository.saveMarketplaceEvent(
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
            liveUpdateService.emitTargetsUpdated();
        });

        return new EventIngestResultRecord(
            eventId,
            MappoMarketplaceEventStatus.applied,
            message,
            targetId
        );
    }

    @Transactional
    public ForwarderLogIngestResultRecord ingestForwarderLog(ForwarderLogIngestRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "forwarder log request is required");
        }

        var command = request.toCommand();
        String logId = normalize(command.logId());
        if (logId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "log_id is required");
        }

        if (adminCommandRepository.forwarderLogExists(logId)) {
            return new ForwarderLogIngestResultRecord(
                logId,
                MappoMarketplaceEventStatus.duplicate,
                "forwarder log already ingested"
            );
        }

        adminCommandRepository.saveForwarderLog(command);
        transactionHookService.afterCommitOrNow(liveUpdateService::emitAdminUpdated);
        return new ForwarderLogIngestResultRecord(
            logId,
            MappoMarketplaceEventStatus.applied,
            "forwarder log ingested"
        );
    }

    @Transactional
    public TargetRegistrationRecord updateTargetRegistration(String targetId, TargetRegistrationPatchRequest patch) {
        TargetRegistrationRecord existing = adminRepository.getRegistration(targetId).orElse(null);
        if (existing == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "target registration not found: " + targetId);
        }
        if (patch == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "registration patch is required");
        }

        adminCommandRepository.updateRegistrationAndTarget(targetId, patch.toCommand());
        transactionHookService.afterCommitOrNow(() -> {
            liveUpdateService.emitAdminUpdated();
            liveUpdateService.emitTargetsUpdated();
        });
        return adminRepository.getRegistration(targetId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "target registration not found: " + targetId));
    }

    @Transactional
    public void deleteTargetRegistration(String targetId) {
        adminCommandRepository.deleteRegistration(targetId);
        targetCommandRepository.deleteTarget(targetId);
        transactionHookService.afterCommitOrNow(() -> {
            liveUpdateService.emitAdminUpdated();
            liveUpdateService.emitTargetsUpdated();
        });
    }

    private Map<String, String> buildTags(OnboardingEventRequest request) {
        Map<String, String> tags = new LinkedHashMap<>(request.effectiveTags());
        putDefault(tags, "ring", defaultIfBlank(normalize(request.targetGroup()), "prod"));
        putDefault(tags, "region", defaultIfBlank(normalize(request.region()), "eastus"));
        putDefault(tags, "environment", defaultIfBlank(normalize(request.environment()), "prod"));
        putDefault(tags, "tier", defaultIfBlank(normalize(request.tier()), "standard"));
        return tags;
    }

    private void putDefault(Map<String, String> tags, String key, String value) {
        if (!tags.containsKey(key) && !value.isBlank()) {
            tags.put(key, value);
        }
    }

    private String resolveTargetId(OnboardingEventRequest request) {
        String targetId = normalize(request.targetId());
        if (!targetId.isBlank()) {
            return targetId;
        }

        String displayName = normalize(request.displayName());
        if (!displayName.isBlank()) {
            return normalizeId(displayName);
        }

        String managedAppId = normalize(request.managedApplicationId());
        if (!managedAppId.isBlank()) {
            int idx = managedAppId.lastIndexOf("/");
            if (idx >= 0 && idx < managedAppId.length() - 1) {
                return normalizeId(managedAppId.substring(idx + 1));
            }
        }

        String containerAppName = normalize(request.containerAppName());
        if (!containerAppName.isBlank()) {
            return normalizeId(containerAppName);
        }

        return "target-" + Math.abs((normalize(request.subscriptionId()) + "-" + normalize(request.tenantId())).hashCode());
    }

    private String normalizeId(String value) {
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return normalized.isBlank() ? "target-generated" : normalized;
    }

    private String deriveResourceGroupIdFromContainerApp(String containerAppId) {
        int idx = containerAppId.toLowerCase().indexOf("/providers/");
        if (idx > 0) {
            return containerAppId.substring(0, idx);
        }
        return "";
    }

    private String defaultDeploymentStackName(String targetId) {
        String normalized = normalizeId(targetId);
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48);
        }
        return "mappo-stack-" + normalized;
    }

    private MappoRegistryAuthMode defaultRegistryAuthMode() {
        return hasSharedPublisherAcrConfig()
            ? MappoRegistryAuthMode.shared_service_principal_secret
            : MappoRegistryAuthMode.none;
    }

    private boolean hasSharedPublisherAcrConfig() {
        return !normalize(properties.getPublisherAcr().getServer()).isBlank()
            && !normalize(properties.getPublisherAcr().getPullClientId()).isBlank()
            && !normalize(properties.getPublisherAcr().getPullSecretName()).isBlank();
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
