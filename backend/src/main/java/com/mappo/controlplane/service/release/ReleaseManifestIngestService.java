package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.application.release.GithubReleaseWebhookHandler;
import com.mappo.controlplane.api.request.ReleaseManifestIngestRequest;
import com.mappo.controlplane.application.release.AzureDevOpsReleaseWebhookHandler;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseManifestIngestService {

    private final ReleaseManifestSourceClient sourceClient;
    private final ReleaseManifestParser releaseManifestParser;
    private final ReleaseManifestApplyService releaseManifestApplyService;
    private final GithubReleaseWebhookHandler githubReleaseWebhookHandler;
    private final AzureDevOpsReleaseWebhookHandler azureDevOpsReleaseWebhookHandler;
    private final ProjectCatalogService projectCatalogService;
    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final MappoProperties properties;

    public ReleaseManifestIngestResultRecord ingestGithubManifest(ReleaseManifestIngestRequest request) {
        String projectId = normalize(request == null ? null : request.projectId());
        ReleaseIngestEndpointRecord projectEndpoint = resolveGithubProjectEndpoint(projectId);
        String repo = normalize(firstNonBlank(
            request == null ? null : request.repo(),
            projectEndpoint == null ? null : projectEndpoint.repoFilter(),
            properties.getManagedAppRelease().getRepo()
        ));
        String path = normalize(firstNonBlank(
            request == null ? null : request.path(),
            projectEndpoint == null ? null : projectEndpoint.manifestPath(),
            properties.getManagedAppRelease().getPath()
        ));
        String ref = normalize(firstNonBlank(
            request == null ? null : request.ref(),
            projectEndpoint == null ? null : projectEndpoint.branchFilter(),
            properties.getManagedAppRelease().getRef()
        ));
        boolean allowDuplicates = request != null && Boolean.TRUE.equals(request.allowDuplicates());

        if (repo.isBlank() || path.isBlank() || ref.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "repo, path, and ref are required for release manifest ingest");
        }

        String manifest = sourceClient.fetchGithubManifest(repo, path, ref);
        ParsedReleaseManifest parsedManifest = releaseManifestParser.parse(manifest);
        List<String> fallbackProjectIds = projectId.isBlank() ? List.of() : List.of(projectId);
        return releaseManifestApplyService.apply(repo, path, ref, allowDuplicates, parsedManifest, fallbackProjectIds);
    }

    public ReleaseManifestIngestResultRecord ingestGithubWebhook(
        String rawPayload,
        String githubEvent,
        String signatureHeader,
        String githubDeliveryId
    ) {
        return githubReleaseWebhookHandler.handle(null, rawPayload, githubEvent, signatureHeader, githubDeliveryId);
    }

    public ReleaseManifestIngestResultRecord ingestGithubWebhookForEndpoint(
        String endpointId,
        String rawPayload,
        String githubEvent,
        String signatureHeader,
        String githubDeliveryId
    ) {
        return githubReleaseWebhookHandler.handle(endpointId, rawPayload, githubEvent, signatureHeader, githubDeliveryId);
    }

    public ReleaseManifestIngestResultRecord ingestAzureDevOpsWebhook(
        String rawPayload,
        String eventTypeHeader,
        String deliveryIdHeader,
        String authorizationHeader,
        String queryToken,
        String projectId
    ) {
        return azureDevOpsReleaseWebhookHandler.handle(
            null,
            rawPayload,
            eventTypeHeader,
            deliveryIdHeader,
            authorizationHeader,
            queryToken,
            projectId
        );
    }

    public ReleaseManifestIngestResultRecord ingestAzureDevOpsWebhookForEndpoint(
        String endpointId,
        String rawPayload,
        String eventTypeHeader,
        String deliveryIdHeader,
        String authorizationHeader,
        String queryToken,
        String projectId
    ) {
        return azureDevOpsReleaseWebhookHandler.handle(
            endpointId,
            rawPayload,
            eventTypeHeader,
            deliveryIdHeader,
            authorizationHeader,
            queryToken,
            projectId
        );
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

    private ReleaseIngestEndpointRecord resolveGithubProjectEndpoint(String projectId) {
        String normalizedProjectId = normalize(projectId);
        if (normalizedProjectId.isBlank()) {
            return null;
        }
        ProjectDefinition project = projectCatalogService.getRequired(normalizedProjectId);
        String endpointId = normalize(project.releaseIngestEndpointId());
        if (endpointId.isBlank()) {
            return null;
        }
        ReleaseIngestEndpointRecord endpoint = releaseIngestEndpointCatalogService.getRequired(endpointId);
        if (endpoint.provider() != com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType.github) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "project %s is not linked to a GitHub release source".formatted(normalizedProjectId)
            );
        }
        return endpoint;
    }
}
