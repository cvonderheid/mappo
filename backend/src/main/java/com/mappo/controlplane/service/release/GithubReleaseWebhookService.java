package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import com.mappo.controlplane.model.ReleaseIngestLinkedProjectRecord;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointCatalogService;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestSecretResolver;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GithubReleaseWebhookService {

    private final GithubWebhookDecisionService githubWebhookDecisionService;
    private final GithubWebhookSignatureService githubWebhookSignatureService;
    private final GithubWebhookPayloadService githubWebhookPayloadService;
    private final ReleaseWebhookAuditService releaseWebhookAuditService;
    private final ReleaseManifestSourceClient sourceClient;
    private final ReleaseManifestParser releaseManifestParser;
    private final ReleaseManifestApplyService releaseManifestApplyService;
    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ReleaseIngestSecretResolver releaseIngestSecretResolver;

    public ReleaseManifestIngestResultRecord handle(
        String endpointId,
        String rawPayload,
        String githubEvent,
        String signatureHeader,
        String githubDeliveryId
    ) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String deliveryLogId = releaseWebhookAuditService.newDeliveryLogId(githubDeliveryId, githubEvent, rawPayload, receivedAt);
        String normalizedEvent = githubWebhookDecisionService.normalizeEvent(githubEvent);
        ReleaseIngestEndpointRecord endpoint = resolveEndpoint(endpointId);
        String manifestPath = githubWebhookDecisionService.manifestPath(endpoint);
        String repo = "";
        String ref = "";
        List<String> changedPaths = List.of();

        String configuredSecret = endpoint == null
            ? githubWebhookDecisionService.configuredSecret()
            : releaseIngestSecretResolver.resolveConfiguredSecret(endpoint);
        if (configuredSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "managed app release webhook secret is not configured");
        }
        if (normalize(rawPayload).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook payload is required");
        }

        try {
            githubWebhookSignatureService.verify(rawPayload, signatureHeader, configuredSecret);
            GithubWebhookPayloadRecord payload = githubWebhookPayloadService.parse(rawPayload, manifestPath);
            repo = payload.repo();
            ref = payload.ref();
            changedPaths = payload.changedPaths();
            githubWebhookDecisionService.assertRepoAllowed(repo, endpoint);
            GithubWebhookDecision decision = githubWebhookDecisionService.decide(normalizedEvent, payload, endpoint);
            if (!decision.processManifest()) {
                ReleaseManifestIngestResultRecord result = emptyResult(repo, manifestPath, ref);
                releaseWebhookAuditService.logWebhookDelivery(
                    deliveryLogId,
                    githubDeliveryId,
                    normalizedEvent,
                    repo,
                    ref,
                    manifestPath,
                    decision.status(),
                    decision.message(),
                    changedPaths,
                    result,
                    receivedAt
                );
                return result;
            }

            String manifest = sourceClient.fetchGithubManifest(repo, manifestPath, ref);
            ParsedReleaseManifest parsedManifest = releaseManifestParser.parse(manifest);
            ReleaseManifestIngestResultRecord result = releaseManifestApplyService.apply(
                repo,
                manifestPath,
                ref,
                false,
                parsedManifest,
                endpoint == null
                    ? List.of()
                    : endpoint.linkedProjects().stream()
                        .map(ReleaseIngestLinkedProjectRecord::projectId)
                        .filter(projectId -> !normalize(projectId).isBlank())
                        .toList()
            );
            MappoReleaseWebhookStatus status = result.createdCount() > 0
                ? MappoReleaseWebhookStatus.applied
                : MappoReleaseWebhookStatus.skipped;
            String message = result.createdCount() > 0
                ? "Processed GitHub release webhook and created " + result.createdCount() + " release(s)."
                : "Processed GitHub release webhook; no new releases were created.";
            releaseWebhookAuditService.logWebhookDelivery(
                deliveryLogId,
                githubDeliveryId,
                normalizedEvent,
                repo,
                ref,
                manifestPath,
                status,
                message,
                changedPaths,
                result,
                receivedAt
            );
            return result;
        } catch (RuntimeException exception) {
            releaseWebhookAuditService.logWebhookDelivery(
                deliveryLogId,
                githubDeliveryId,
                normalizedEvent,
                repo,
                ref,
                manifestPath,
                MappoReleaseWebhookStatus.failed,
                normalize(exception.getMessage()),
                changedPaths,
                null,
                receivedAt
            );
            throw exception;
        }
    }

    private ReleaseIngestEndpointRecord resolveEndpoint(String endpointId) {
        String normalizedEndpointId = normalize(endpointId);
        if (normalizedEndpointId.isBlank()) {
            return null;
        }
        ReleaseIngestEndpointRecord endpoint = releaseIngestEndpointCatalogService.getRequired(normalizedEndpointId);
        if (endpoint.provider() != ReleaseIngestProviderType.github) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "release source %s is not configured for GitHub webhooks".formatted(normalizedEndpointId)
            );
        }
        if (!endpoint.enabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source is disabled: " + normalizedEndpointId);
        }
        return endpoint;
    }

    private ReleaseManifestIngestResultRecord emptyResult(String repo, String manifestPath, String ref) {
        return new ReleaseManifestIngestResultRecord(repo, manifestPath, ref, 0, 0, 0, 0, List.of());
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
