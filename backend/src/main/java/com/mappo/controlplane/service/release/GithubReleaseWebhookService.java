package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.command.ReleaseWebhookDeliveryCommand;
import com.mappo.controlplane.repository.ReleaseWebhookRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GithubReleaseWebhookService {

    private static final String GITHUB_PUSH_EVENT = "push";
    private static final String GITHUB_PING_EVENT = "ping";
    private static final String GITHUB_SIGNATURE_PREFIX = "sha256=";

    private final MappoProperties properties;
    private final ReleaseWebhookRepository releaseWebhookRepository;
    private final ReleaseManifestSourceClient sourceClient;
    private final ReleaseManifestParser releaseManifestParser;
    private final ReleaseManifestApplyService releaseManifestApplyService;
    private final LiveUpdateService liveUpdateService;

    public ReleaseManifestIngestResultRecord handle(String rawPayload, String githubEvent, String signatureHeader, String githubDeliveryId) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String deliveryLogId = newDeliveryLogId(githubDeliveryId, githubEvent, rawPayload, receivedAt);
        String normalizedEvent = normalize(githubEvent).toLowerCase();
        String manifestPath = normalize(properties.getManagedAppRelease().getPath()).replaceFirst("^/+", "");
        String repo = "";
        String ref = "";
        List<String> changedPaths = List.of();

        String configuredSecret = normalize(properties.getManagedAppRelease().getWebhookSecret());
        if (configuredSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "managed app release webhook secret is not configured");
        }
        if (normalize(rawPayload).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook payload is required");
        }

        try {
            verifyGithubSignature(rawPayload, signatureHeader, configuredSecret);
            Map<?, ?> payload = releaseManifestParser.readJsonObject(rawPayload, "github webhook payload is not valid JSON");
            repo = normalize(readNestedValue(payload, "repository", "full_name"));
            if (repo.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook payload missing repository.full_name");
            }

            String expectedRepo = normalize(properties.getManagedAppRelease().getRepo());
            if (!expectedRepo.isBlank() && !expectedRepo.equals(repo)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook repo is not allowed: " + repo);
            }

            ref = normalizeGithubRef(normalize(payload.get("ref")));
            changedPaths = extractChangedPaths(payload);

            if (GITHUB_PING_EVENT.equals(normalizedEvent)) {
                ReleaseManifestIngestResultRecord result = new ReleaseManifestIngestResultRecord(
                    repo,
                    manifestPath,
                    properties.getManagedAppRelease().getRef(),
                    0,
                    0,
                    0,
                    0,
                    List.of()
                );
                logWebhookDelivery(
                    deliveryLogId,
                    githubDeliveryId,
                    normalizedEvent,
                    repo,
                    ref,
                    manifestPath,
                    MappoReleaseWebhookStatus.skipped,
                    "GitHub webhook ping acknowledged.",
                    changedPaths,
                    result,
                    receivedAt
                );
                return result;
            }
            if (!GITHUB_PUSH_EVENT.equals(normalizedEvent)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported github webhook event: " + githubEvent);
            }

            String expectedRef = normalize(properties.getManagedAppRelease().getRef());
            if (!expectedRef.isBlank() && !expectedRef.equals(ref)) {
                ReleaseManifestIngestResultRecord result = new ReleaseManifestIngestResultRecord(
                    repo,
                    manifestPath,
                    ref,
                    0,
                    0,
                    0,
                    0,
                    List.of()
                );
                logWebhookDelivery(
                    deliveryLogId,
                    githubDeliveryId,
                    normalizedEvent,
                    repo,
                    ref,
                    manifestPath,
                    MappoReleaseWebhookStatus.skipped,
                    "Ignored webhook push on non-configured ref " + ref + ".",
                    changedPaths,
                    result,
                    receivedAt
                );
                return result;
            }
            if (!pushTouchesPath(payload, manifestPath)) {
                ReleaseManifestIngestResultRecord result = new ReleaseManifestIngestResultRecord(
                    repo,
                    manifestPath,
                    ref,
                    0,
                    0,
                    0,
                    0,
                    List.of()
                );
                logWebhookDelivery(
                    deliveryLogId,
                    githubDeliveryId,
                    normalizedEvent,
                    repo,
                    ref,
                    manifestPath,
                    MappoReleaseWebhookStatus.skipped,
                    "Ignored webhook push because the managed-app release manifest did not change.",
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
                parsedManifest
            );
            MappoReleaseWebhookStatus status = result.createdCount() > 0
                ? MappoReleaseWebhookStatus.applied
                : MappoReleaseWebhookStatus.skipped;
            String message = result.createdCount() > 0
                ? "Processed GitHub release webhook and created " + result.createdCount() + " release(s)."
                : "Processed GitHub release webhook; no new releases were created.";
            logWebhookDelivery(
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
            logWebhookDelivery(
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

    private void verifyGithubSignature(String rawPayload, String signatureHeader, String secret) {
        String provided = normalize(signatureHeader);
        if (!provided.startsWith(GITHUB_SIGNATURE_PREFIX)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "missing github webhook signature");
        }

        String expected = GITHUB_SIGNATURE_PREFIX + hmacSha256Hex(secret, rawPayload);
        if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid github webhook signature");
        }
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to verify github webhook signature");
        }
    }

    private Object readNestedValue(Map<?, ?> source, String parentKey, String childKey) {
        Object parent = source.get(parentKey);
        if (!(parent instanceof Map<?, ?> parentMap)) {
            return null;
        }
        return parentMap.get(childKey);
    }

    private boolean pushTouchesPath(Map<?, ?> payload, String manifestPath) {
        Object commitsValue = payload.get("commits");
        if (!(commitsValue instanceof List<?> commits)) {
            return false;
        }
        for (Object commit : commits) {
            if (!(commit instanceof Map<?, ?> commitMap)) {
                continue;
            }
            if (pathsContain(commitMap.get("added"), manifestPath)
                || pathsContain(commitMap.get("modified"), manifestPath)
                || pathsContain(commitMap.get("removed"), manifestPath)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractChangedPaths(Map<?, ?> payload) {
        Object commitsValue = payload.get("commits");
        if (!(commitsValue instanceof List<?> commits)) {
            return List.of();
        }
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (Object commit : commits) {
            if (!(commit instanceof Map<?, ?> commitMap)) {
                continue;
            }
            addPaths(paths, commitMap.get("added"));
            addPaths(paths, commitMap.get("modified"));
            addPaths(paths, commitMap.get("removed"));
        }
        return List.copyOf(paths);
    }

    private void addPaths(Set<String> target, Object value) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            String path = normalize(item).replaceFirst("^/+", "");
            if (!path.isBlank()) {
                target.add(path);
            }
        }
    }

    private boolean pathsContain(Object value, String targetPath) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (targetPath.equals(normalize(item).replaceFirst("^/+", ""))) {
                return true;
            }
        }
        return false;
    }

    private void logWebhookDelivery(
        String deliveryLogId,
        String externalDeliveryId,
        String eventType,
        String repo,
        String ref,
        String manifestPath,
        MappoReleaseWebhookStatus status,
        String message,
        List<String> changedPaths,
        ReleaseManifestIngestResultRecord result,
        OffsetDateTime receivedAt
    ) {
        releaseWebhookRepository.saveDelivery(new ReleaseWebhookDeliveryCommand(
            deliveryLogId,
            nullable(externalDeliveryId),
            defaultIfBlank(eventType, "unknown"),
            nullable(repo),
            nullable(ref),
            nullable(manifestPath),
            status,
            defaultIfBlank(message, "GitHub release webhook processed."),
            changedPaths == null ? List.of() : List.copyOf(changedPaths),
            result == null ? 0 : result.manifestReleaseCount(),
            result == null ? 0 : result.createdCount(),
            result == null ? 0 : result.skippedCount(),
            result == null ? 0 : result.ignoredCount(),
            result == null ? List.of() : result.createdReleaseIds(),
            receivedAt
        ));
        liveUpdateService.emitAdminUpdated();
    }

    private String newDeliveryLogId(String externalDeliveryId, String eventType, String payload, OffsetDateTime receivedAt) {
        String basis = firstNonBlank(normalize(externalDeliveryId), normalize(eventType), "github")
            + "::" + normalize(payload)
            + "::" + receivedAt.toInstant().toEpochMilli();
        return "rwh-" + sha256Hex(basis).substring(0, 12);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to hash webhook delivery");
        }
    }

    private String normalizeGithubRef(String ref) {
        String normalized = normalize(ref);
        if (normalized.startsWith("refs/heads/")) {
            return normalized.substring("refs/heads/".length());
        }
        if (normalized.startsWith("refs/tags/")) {
            return normalized.substring("refs/tags/".length());
        }
        return normalized;
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

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
