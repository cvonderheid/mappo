package com.mappo.controlplane.service.admin;

import com.mappo.controlplane.api.request.OnboardingEventRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.model.command.TargetRegistrationUpsertCommand;
import com.mappo.controlplane.model.command.TargetUpsertCommand;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketplaceOnboardingTargetFactory {

    private final MappoProperties properties;
    private final ProjectCatalogService projectCatalogService;

    public MarketplaceOnboardingTargetPlan create(
        OnboardingEventRequest request,
        String eventId,
        OffsetDateTime now
    ) {
        String targetId = resolveTargetId(request);
        String projectId = resolveProjectId(request.projectId());
        String containerAppResourceId = normalize(request.containerAppResourceId());
        String managedResourceGroupId = normalize(request.managedResourceGroupId());
        if (managedResourceGroupId.isBlank()) {
            managedResourceGroupId = deriveResourceGroupIdFromContainerApp(containerAppResourceId);
        }
        Map<String, String> executionConfig = resolveExecutionConfig(request);

        return new MarketplaceOnboardingTargetPlan(
            targetId,
            new TargetUpsertCommand(
                targetId,
                projectId,
                request.tenantId(),
                request.subscriptionId(),
                buildTags(request),
                defaultIfBlank(normalize(request.lastDeployedRelease()), "unknown"),
                request.healthStatus() == null ? MappoHealthStatus.registered : request.healthStatus(),
                now,
                MappoSimulatedFailureMode.none
            ),
            new TargetRegistrationUpsertCommand(
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
                executionConfig,
                eventId,
                now
            ),
            executionConfig
        );
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
        return generatedTargetId(request);
    }

    private String generatedTargetId(OnboardingEventRequest request) {
        String material = String.join(
            "|",
            normalize(request.projectId()),
            normalize(request.tenantId()),
            normalize(request.subscriptionId()),
            normalize(request.managedApplicationId()),
            normalize(request.managedResourceGroupId()),
            normalize(request.containerAppResourceId()),
            normalize(request.containerAppName()),
            normalize(request.marketplacePayloadId())
        );
        return "tgt-" + sha256Hex(material).substring(0, 16);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append("%02x".formatted(b & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
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

    private String resolveProjectId(String explicitProjectId) {
        String normalized = normalize(explicitProjectId);
        if (!normalized.isBlank()) {
            return projectCatalogService.resolveRequiredProjectId(normalized);
        }
        return projectCatalogService.getRequired(BuiltinProjects.AZURE_MANAGED_APP_DEPLOYMENT_STACK).id();
    }

    private Map<String, String> resolveExecutionConfig(OnboardingEventRequest request) {
        if (request.metadata() == null) {
            return Map.of();
        }
        Map<String, String> executionConfig = request.metadata().sanitizedExecutionConfig();
        return executionConfig == null || executionConfig.isEmpty() ? Map.of() : Map.copyOf(executionConfig);
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
