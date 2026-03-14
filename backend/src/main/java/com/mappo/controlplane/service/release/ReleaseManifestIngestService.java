package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseManifestIngestRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseManifestIngestService {

    private final ReleaseManifestSourceClient sourceClient;
    private final ReleaseManifestParser releaseManifestParser;
    private final ReleaseManifestApplyService releaseManifestApplyService;
    private final GithubReleaseWebhookService githubReleaseWebhookService;
    private final AzureDevOpsReleaseWebhookService azureDevOpsReleaseWebhookService;
    private final MappoProperties properties;

    public ReleaseManifestIngestResultRecord ingestGithubManifest(ReleaseManifestIngestRequest request) {
        String repo = normalize(firstNonBlank(request == null ? null : request.repo(), properties.getManagedAppRelease().getRepo()));
        String path = normalize(firstNonBlank(request == null ? null : request.path(), properties.getManagedAppRelease().getPath()));
        String ref = normalize(firstNonBlank(request == null ? null : request.ref(), properties.getManagedAppRelease().getRef()));
        boolean allowDuplicates = request != null && Boolean.TRUE.equals(request.allowDuplicates());

        if (repo.isBlank() || path.isBlank() || ref.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "repo, path, and ref are required for release manifest ingest");
        }

        String manifest = sourceClient.fetchGithubManifest(repo, path, ref);
        ParsedReleaseManifest parsedManifest = releaseManifestParser.parse(manifest);
        return releaseManifestApplyService.apply(repo, path, ref, allowDuplicates, parsedManifest);
    }

    public ReleaseManifestIngestResultRecord ingestGithubWebhook(
        String rawPayload,
        String githubEvent,
        String signatureHeader,
        String githubDeliveryId
    ) {
        return githubReleaseWebhookService.handle(rawPayload, githubEvent, signatureHeader, githubDeliveryId);
    }

    public ReleaseManifestIngestResultRecord ingestAzureDevOpsWebhook(
        String rawPayload,
        String eventTypeHeader,
        String deliveryIdHeader,
        String authorizationHeader,
        String queryToken,
        String projectId
    ) {
        return azureDevOpsReleaseWebhookService.handle(
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
}
