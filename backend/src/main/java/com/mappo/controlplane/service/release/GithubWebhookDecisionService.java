package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GithubWebhookDecisionService {

    private static final String GITHUB_PUSH_EVENT = "push";
    private static final String GITHUB_PING_EVENT = "ping";

    private final MappoProperties properties;

    public String manifestPath() {
        return normalize(properties.getManagedAppRelease().getPath()).replaceFirst("^/+", "");
    }

    public String configuredSecret() {
        return normalize(properties.getManagedAppRelease().getWebhookSecret());
    }

    public String normalizeEvent(String githubEvent) {
        return normalize(githubEvent).toLowerCase();
    }

    public void assertRepoAllowed(String repo) {
        String expectedRepo = normalize(properties.getManagedAppRelease().getRepo());
        if (!expectedRepo.isBlank() && !expectedRepo.equals(repo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook repo is not allowed: " + repo);
        }
    }

    public GithubWebhookDecision decide(String normalizedEvent, GithubWebhookPayloadRecord payload) {
        if (GITHUB_PING_EVENT.equals(normalizedEvent)) {
            return new GithubWebhookDecision(
                false,
                MappoReleaseWebhookStatus.skipped,
                "GitHub webhook ping acknowledged."
            );
        }
        if (!GITHUB_PUSH_EVENT.equals(normalizedEvent)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported github webhook event: " + normalizedEvent);
        }

        String expectedRef = normalize(properties.getManagedAppRelease().getRef());
        if (!expectedRef.isBlank() && !expectedRef.equals(payload.ref())) {
            return new GithubWebhookDecision(
                false,
                MappoReleaseWebhookStatus.skipped,
                "Ignored webhook push on non-configured ref " + payload.ref() + "."
            );
        }
        if (!payload.touchesManifestPath()) {
            return new GithubWebhookDecision(
                false,
                MappoReleaseWebhookStatus.skipped,
                "Ignored webhook push because the managed-app release manifest did not change."
            );
        }
        return new GithubWebhookDecision(true, null, "");
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
