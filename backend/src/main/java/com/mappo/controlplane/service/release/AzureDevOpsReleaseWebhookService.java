package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import com.mappo.controlplane.api.request.ReleaseExecutionSettingsRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointCatalogService;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestSecretResolver;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AzureDevOpsReleaseWebhookService {

    private final AzureDevOpsReleaseWebhookPayloadService payloadService;
    private final ProjectCatalogService projectCatalogService;
    private final ReleaseWebhookAuditService releaseWebhookAuditService;
    private final ReleaseManifestApplyService releaseManifestApplyService;
    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ReleaseIngestSecretResolver releaseIngestSecretResolver;
    private final MappoProperties properties;

    public ReleaseManifestIngestResultRecord handle(
        String rawPayload,
        String eventTypeHeader,
        String deliveryIdHeader,
        String authorizationHeader,
        String queryToken,
        String projectId
    ) {
        return handle(
            null,
            rawPayload,
            eventTypeHeader,
            deliveryIdHeader,
            authorizationHeader,
            queryToken,
            projectId
        );
    }

    public ReleaseManifestIngestResultRecord handle(
        String endpointId,
        String rawPayload,
        String eventTypeHeader,
        String deliveryIdHeader,
        String authorizationHeader,
        String queryToken,
        String projectId
    ) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        AzureDevOpsReleaseWebhookPayloadRecord payload = payloadService.parse(rawPayload, eventTypeHeader, deliveryIdHeader);
        String normalizedEvent = normalize(payload.eventType()).toLowerCase();
        String resolvedProjectId = normalize(projectId).isBlank()
            ? BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE
            : normalize(projectId);
        String deliveryLogId = releaseWebhookAuditService.newDeliveryLogId(
            payload.deliveryId(),
            normalizedEvent,
            rawPayload,
            receivedAt
        );

        ReleaseIngestEndpointRecord endpoint = resolveEndpoint(endpointId);
        validateAuthentication(endpoint, authorizationHeader, queryToken);

        if (resolvedProjectId.equals(BuiltinProjects.AZURE_APPSERVICE_ADO_PIPELINE)
            && endpoint != null
            && endpoint.linkedProjects() != null
            && !endpoint.linkedProjects().isEmpty()) {
            resolvedProjectId = endpoint.linkedProjects().getFirst().projectId();
        }

        ProjectDefinition project = projectCatalogService.getRequired(resolvedProjectId);
        PipelineTriggerDriverConfig pipelineConfig = requireAdoPipelineProject(project);

        String repo = repoLabel(pipelineConfig, payload);
        String ref = firstNonBlank(payload.branch(), normalize(pipelineConfig.branch()));
        String manifestPath = "pipelines/" + firstNonBlank(payload.pipelineId(), pipelineConfig.pipelineId()) + "/runs/" + payload.runId();

        try {
            if (!isActionableEvent(normalizedEvent)) {
                ReleaseManifestIngestResultRecord result = emptyResult(repo, "azure-devops-service-hook", ref);
                releaseWebhookAuditService.logWebhookDelivery(
                    deliveryLogId,
                    payload.deliveryId(),
                    normalizedEvent,
                    repo,
                    ref,
                    manifestPath,
                    MappoReleaseWebhookStatus.skipped,
                    "Ignored Azure DevOps webhook event type " + normalizedEvent + ".",
                    List.of(),
                    result,
                    receivedAt
                );
                return result;
            }

            String pipelineId = firstNonBlank(payload.pipelineId(), normalize(pipelineConfig.pipelineId()));
            if (!matchesConfiguredPipeline(endpoint, pipelineConfig, pipelineId)) {
                ReleaseManifestIngestResultRecord result = emptyResult(repo, "azure-devops-service-hook", ref);
                releaseWebhookAuditService.logWebhookDelivery(
                    deliveryLogId,
                    payload.deliveryId(),
                    normalizedEvent,
                    repo,
                    ref,
                    manifestPath,
                    MappoReleaseWebhookStatus.skipped,
                    "Ignored Azure DevOps webhook for non-configured pipeline " + pipelineId + ".",
                    List.of(),
                    result,
                    receivedAt
                );
                return result;
            }

            if (!matchesConfiguredBranch(endpoint, pipelineConfig, payload.branch())) {
                ReleaseManifestIngestResultRecord result = emptyResult(repo, "azure-devops-service-hook", ref);
                releaseWebhookAuditService.logWebhookDelivery(
                    deliveryLogId,
                    payload.deliveryId(),
                    normalizedEvent,
                    repo,
                    ref,
                    manifestPath,
                    MappoReleaseWebhookStatus.skipped,
                    "Ignored Azure DevOps webhook for non-configured branch " + payload.branch() + ".",
                    List.of(),
                    result,
                    receivedAt
                );
                return result;
            }

            if (!"succeeded".equalsIgnoreCase(normalize(payload.runResult()))) {
                ReleaseManifestIngestResultRecord result = emptyResult(repo, "azure-devops-service-hook", ref);
                releaseWebhookAuditService.logWebhookDelivery(
                    deliveryLogId,
                    payload.deliveryId(),
                    normalizedEvent,
                    repo,
                    ref,
                    manifestPath,
                    MappoReleaseWebhookStatus.skipped,
                    "Ignored Azure DevOps webhook because run result is " + payload.runResult() + ".",
                    List.of(),
                    result,
                    receivedAt
                );
                return result;
            }

            String runId = normalize(payload.runId());
            String sourceVersion = firstNonBlank(payload.runName(), runId);
            if (runId.isBlank() || sourceVersion.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "azure devops webhook payload missing run identity");
            }

            String sourceRef = sourceRef(pipelineConfig, payload, pipelineId);
            String sourceVersionRef = firstNonBlank(payload.runWebUrl(), payload.runApiUrl(), sourceRef + "/runs/" + runId);
            ReleaseCreateRequest releaseRequest = new ReleaseCreateRequest(
                resolvedProjectId,
                sourceRef,
                sourceVersion,
                MappoReleaseSourceType.external_deployment_inputs,
                sourceVersionRef,
                MappoDeploymentScope.resource_group,
                new ReleaseExecutionSettingsRequest(null, false, true),
                Map.of("appVersion", sourceVersion),
                Map.of("artifactVersion", sourceVersion, "deployedBy", "azure-devops-service-hook"),
                "Azure DevOps run %s (%s) completed successfully.".formatted(sourceVersion, runId),
                List.of()
            );

            ReleaseManifestIngestResultRecord result = releaseManifestApplyService.apply(
                repo,
                "azure-devops-service-hook",
                ref,
                false,
                new ParsedReleaseManifest(1, 0, List.of(releaseRequest))
            );
            MappoReleaseWebhookStatus status = result.createdCount() > 0
                ? MappoReleaseWebhookStatus.applied
                : MappoReleaseWebhookStatus.skipped;
            String message = result.createdCount() > 0
                ? "Processed Azure DevOps webhook and created " + result.createdCount() + " release(s)."
                : "Processed Azure DevOps webhook; no new releases were created.";
            releaseWebhookAuditService.logWebhookDelivery(
                deliveryLogId,
                payload.deliveryId(),
                normalizedEvent,
                repo,
                ref,
                manifestPath,
                status,
                message,
                List.of(),
                result,
                receivedAt
            );
            return result;
        } catch (RuntimeException exception) {
            releaseWebhookAuditService.logWebhookDelivery(
                deliveryLogId,
                payload.deliveryId(),
                normalizedEvent,
                repo,
                ref,
                manifestPath,
                MappoReleaseWebhookStatus.failed,
                normalize(exception.getMessage()),
                List.of(),
                null,
                receivedAt
            );
            throw exception;
        }
    }

    private void validateAuthentication(String authorizationHeader, String queryToken) {
        validateAuthentication(null, authorizationHeader, queryToken);
    }

    private void validateAuthentication(
        ReleaseIngestEndpointRecord endpoint,
        String authorizationHeader,
        String queryToken
    ) {
        String configuredSecret = endpoint == null
            ? normalize(properties.getAzureDevOps().getWebhookSecret())
            : releaseIngestSecretResolver.resolveConfiguredSecret(endpoint);
        if (configuredSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "azure devops webhook secret is not configured");
        }
        String supplied = firstNonBlank(normalize(queryToken), basicPassword(authorizationHeader));
        if (supplied.isBlank() || !configuredSecret.equals(supplied)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid azure devops webhook token");
        }
    }

    private PipelineTriggerDriverConfig requireAdoPipelineProject(ProjectDefinition project) {
        if (project.deploymentDriver() != ProjectDeploymentDriverType.pipeline_trigger
            || !(project.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig pipelineConfig)
            || !"azure_devops".equalsIgnoreCase(normalize(pipelineConfig.pipelineSystem()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project does not support azure devops pipeline webhook ingest");
        }
        return pipelineConfig;
    }

    private boolean isActionableEvent(String normalizedEvent) {
        if (normalizedEvent.isBlank()) {
            return true;
        }
        return normalizedEvent.contains("run-state-changed")
            || normalizedEvent.contains("build.complete")
            || normalizedEvent.contains("pipeline");
    }

    private boolean matchesConfiguredPipeline(
        ReleaseIngestEndpointRecord endpoint,
        PipelineTriggerDriverConfig config,
        String pipelineId
    ) {
        String configured = endpoint == null
            ? normalize(config.pipelineId())
            : firstNonBlank(normalize(endpoint.pipelineIdFilter()), normalize(config.pipelineId()));
        if (configured.isBlank()) {
            return true;
        }
        return configured.equals(normalize(pipelineId));
    }

    private boolean matchesConfiguredBranch(
        ReleaseIngestEndpointRecord endpoint,
        PipelineTriggerDriverConfig config,
        String branch
    ) {
        String configuredBranch = endpoint == null
            ? normalize(config.branch())
            : firstNonBlank(normalize(endpoint.branchFilter()), normalize(config.branch()));
        if (configuredBranch.isBlank() || normalize(branch).isBlank()) {
            return true;
        }
        return configuredBranch.equalsIgnoreCase(normalize(branch));
    }

    private String sourceRef(
        PipelineTriggerDriverConfig config,
        AzureDevOpsReleaseWebhookPayloadRecord payload,
        String pipelineId
    ) {
        String organization = firstNonBlank(payload.organization(), organizationName(config.organization()));
        String project = firstNonBlank(payload.project(), normalize(config.project()));
        return "ado://%s/%s/pipelines/%s".formatted(organization, project, normalize(pipelineId));
    }

    private String organizationName(String organizationValue) {
        String normalized = normalize(organizationValue);
        if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
            String withoutProtocol = normalized
                .replaceFirst("^https?://", "")
                .replaceFirst("/+$", "");
            if (withoutProtocol.startsWith("dev.azure.com/")) {
                return normalize(withoutProtocol.substring("dev.azure.com/".length()));
            }
            return withoutProtocol;
        }
        return normalized;
    }

    private String repoLabel(PipelineTriggerDriverConfig config, AzureDevOpsReleaseWebhookPayloadRecord payload) {
        String organization = firstNonBlank(payload.organization(), organizationName(config.organization()));
        String project = firstNonBlank(payload.project(), normalize(config.project()));
        return "ado://%s/%s".formatted(organization, project);
    }

    private ReleaseIngestEndpointRecord resolveEndpoint(String endpointId) {
        String normalizedEndpointId = normalize(endpointId);
        if (normalizedEndpointId.isBlank()) {
            return null;
        }
        ReleaseIngestEndpointRecord endpoint = releaseIngestEndpointCatalogService.getRequired(normalizedEndpointId);
        if (endpoint.provider() != ReleaseIngestProviderType.azure_devops) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "release ingest endpoint %s is not configured for azure devops webhooks".formatted(normalizedEndpointId)
            );
        }
        if (!endpoint.enabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release ingest endpoint is disabled: " + normalizedEndpointId);
        }
        return endpoint;
    }

    private String basicPassword(String authorizationHeader) {
        String normalized = normalize(authorizationHeader);
        if (!normalized.regionMatches(true, 0, "Basic ", 0, 6)) {
            return "";
        }
        String encoded = normalize(normalized.substring(6));
        if (encoded.isBlank()) {
            return "";
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                return "";
            }
            return normalize(decoded.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private ReleaseManifestIngestResultRecord emptyResult(String repo, String path, String ref) {
        return new ReleaseManifestIngestResultRecord(repo, path, ref, 0, 0, 0, 0, List.of());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
