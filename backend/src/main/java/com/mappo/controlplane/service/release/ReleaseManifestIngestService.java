package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import com.mappo.controlplane.api.request.ReleaseExecutionSettingsRequest;
import com.mappo.controlplane.api.request.ReleaseManifestIngestRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.command.ReleaseWebhookDeliveryCommand;
import com.mappo.controlplane.repository.ReleaseWebhookRepository;
import com.mappo.controlplane.service.ReleaseService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReleaseManifestIngestService {

    private static final String GITHUB_PUSH_EVENT = "push";
    private static final String GITHUB_PING_EVENT = "ping";
    private static final String GITHUB_SIGNATURE_PREFIX = "sha256=";

    private final ReleaseService releaseService;
    private final ReleaseManifestSourceClient sourceClient;
    private final ReleaseWebhookRepository releaseWebhookRepository;
    private final MappoProperties properties;
    private final ObjectMapper objectMapper;

    public ReleaseManifestIngestResultRecord ingestGithubManifest(ReleaseManifestIngestRequest request) {
        String repo = normalize(firstNonBlank(request == null ? null : request.repo(), properties.getManagedAppReleaseRepo()));
        String path = normalize(firstNonBlank(request == null ? null : request.path(), properties.getManagedAppReleasePath()));
        String ref = normalize(firstNonBlank(request == null ? null : request.ref(), properties.getManagedAppReleaseRef()));
        boolean allowDuplicates = request != null && Boolean.TRUE.equals(request.allowDuplicates());

        if (repo.isBlank() || path.isBlank() || ref.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "repo, path, and ref are required for release manifest ingest");
        }

        String manifest = sourceClient.fetchGithubManifest(repo, path, ref);
        return ingestManifest(repo, path, ref, allowDuplicates, manifest);
    }

    public ReleaseManifestIngestResultRecord ingestGithubWebhook(
        String rawPayload,
        String githubEvent,
        String signatureHeader,
        String githubDeliveryId
    ) {
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String deliveryLogId = newDeliveryLogId(githubDeliveryId, githubEvent, rawPayload, receivedAt);
        String normalizedEvent = normalize(githubEvent).toLowerCase();
        String manifestPath = normalize(properties.getManagedAppReleasePath()).replaceFirst("^/+", "");
        String repo = "";
        String ref = "";
        List<String> changedPaths = List.of();

        String configuredSecret = normalize(properties.getManagedAppReleaseWebhookSecret());
        if (configuredSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "managed app release webhook secret is not configured");
        }
        if (normalize(rawPayload).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook payload is required");
        }
        try {
            verifyGithubSignature(rawPayload, signatureHeader, configuredSecret);
            Map<?, ?> payload = readJsonObject(rawPayload, "github webhook payload is not valid JSON");
            repo = normalize(readNestedValue(payload, "repository", "full_name"));
            if (repo.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook payload missing repository.full_name");
            }

            String expectedRepo = normalize(properties.getManagedAppReleaseRepo());
            if (!expectedRepo.isBlank() && !expectedRepo.equals(repo)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook repo is not allowed: " + repo);
            }

            ref = normalizeGithubRef(normalize(payload.get("ref")));
            changedPaths = extractChangedPaths(payload);

            if (GITHUB_PING_EVENT.equals(normalizedEvent)) {
                ReleaseManifestIngestResultRecord result = new ReleaseManifestIngestResultRecord(
                    repo,
                    manifestPath,
                    properties.getManagedAppReleaseRef(),
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

            String expectedRef = normalize(properties.getManagedAppReleaseRef());
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

            ReleaseManifestIngestResultRecord result = ingestGithubManifest(
                new ReleaseManifestIngestRequest(repo, manifestPath, ref, false)
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

    private ReleaseManifestIngestResultRecord ingestManifest(
        String repo,
        String path,
        String ref,
        boolean allowDuplicates,
        String manifestPayload
    ) {
        ParsedManifest parsedManifest = parseManifest(manifestPayload);

        Set<String> existingKeys = new LinkedHashSet<>();
        if (!allowDuplicates) {
            for (ReleaseRecord row : releaseService.listReleases()) {
                existingKeys.add(releaseKey(row.sourceRef(), row.sourceVersion()));
            }
        }

        int created = 0;
        int skipped = 0;
        List<String> createdReleaseIds = new ArrayList<>();
        for (ReleaseCreateRequest candidate : parsedManifest.requests()) {
            String key = releaseKey(candidate.sourceRef(), candidate.sourceVersion());
            if (!allowDuplicates && existingKeys.contains(key)) {
                skipped += 1;
                continue;
            }
            ReleaseRecord createdRelease = releaseService.createRelease(candidate);
            created += 1;
            createdReleaseIds.add(createdRelease.id());
            existingKeys.add(key);
        }

        return new ReleaseManifestIngestResultRecord(
            repo,
            path,
            ref,
            parsedManifest.manifestReleaseCount(),
            created,
            skipped,
            parsedManifest.ignoredCount(),
            List.copyOf(createdReleaseIds)
        );
    }

    private ParsedManifest parseManifest(String rawPayload) {
        Object parsed;
        try {
            parsed = objectMapper.readValue(rawPayload, Object.class);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest is not valid JSON: " + exception.getMessage());
        }

        Object releasesPayload;
        if (parsed instanceof List<?> list) {
            releasesPayload = list;
        } else if (parsed instanceof Map<?, ?> map) {
            releasesPayload = map.get("releases");
            if (releasesPayload == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest object must include a 'releases' array");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest must be an array or an object with a 'releases' array");
        }

        if (!(releasesPayload instanceof List<?> releases)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest 'releases' must be an array");
        }

        List<ReleaseCreateRequest> normalized = new ArrayList<>();
        int ignoredCount = 0;
        for (int index = 0; index < releases.size(); index += 1) {
            Object item = releases.get(index);
            if (!(item instanceof Map<?, ?> row)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest row #%d is not an object".formatted(index + 1));
            }
            if (!shouldIngestRow(row, index)) {
                ignoredCount += 1;
                continue;
            }

            String sourceRef = firstNonBlank(
                stringValue(row.get("source_ref")),
                stringValue(row.get("sourceRef")),
                stringValue(row.get("template_spec_id"))
            );
            String sourceVersion = firstNonBlank(
                stringValue(row.get("source_version")),
                stringValue(row.get("sourceVersion")),
                stringValue(row.get("template_spec_version"))
            );
            if (sourceRef.isBlank() || sourceVersion.isBlank()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "release manifest row #%d missing required fields: source_ref and source_version".formatted(index + 1)
                );
            }

            MappoReleaseSourceType sourceType = enumValue(
                firstNonNull(row.get("source_type"), row.get("sourceType")),
                MappoReleaseSourceType.class,
                MappoReleaseSourceType.template_spec,
                index,
                "source_type"
            );
            String sourceVersionRef = nullable(
                firstNonBlank(
                    stringValue(row.get("source_version_ref")),
                    stringValue(row.get("sourceVersionRef")),
                    stringValue(row.get("template_spec_version_id"))
                )
            );
            if (sourceType != MappoReleaseSourceType.template_spec && sourceVersionRef == null) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "release manifest row #%d missing required field: source_version_ref".formatted(index + 1)
                );
            }

            Map<?, ?> executionSettingsRow = asMap(
                firstNonNull(row.get("execution_settings"), row.get("executionSettings"), row.get("deployment_mode_settings"))
            );
            Map<String, String> parameterDefaults = sanitizeStringMap(asMap(firstNonNull(row.get("parameter_defaults"), row.get("parameterDefaults"))));
            List<String> verificationHints = sanitizeStringList(asList(firstNonNull(row.get("verification_hints"), row.get("verificationHints"))));

            ReleaseExecutionSettingsRequest executionSettings = new ReleaseExecutionSettingsRequest(
                enumValue(
                    firstNonNull(executionSettingsRow.get("arm_mode"), executionSettingsRow.get("armMode")),
                    MappoArmDeploymentMode.class,
                    MappoArmDeploymentMode.incremental,
                    index,
                    "execution_settings.arm_mode"
                ),
                booleanValue(
                    firstNonNull(executionSettingsRow.get("what_if_on_canary"), executionSettingsRow.get("whatIfOnCanary")),
                    false
                ),
                booleanValue(
                    firstNonNull(executionSettingsRow.get("verify_after_deploy"), executionSettingsRow.get("verifyAfterDeploy")),
                    true
                )
            );

            normalized.add(new ReleaseCreateRequest(
                sourceRef,
                sourceVersion,
                sourceType,
                sourceVersionRef,
                enumValue(
                    firstNonNull(row.get("deployment_scope"), row.get("deploymentScope")),
                    MappoDeploymentScope.class,
                    MappoDeploymentScope.resource_group,
                    index,
                    "deployment_scope"
                ),
                executionSettings,
                parameterDefaults,
                normalize(firstNonBlank(stringValue(row.get("release_notes")), stringValue(row.get("releaseNotes")))),
                verificationHints
            ));
        }

        return new ParsedManifest(releases.size(), ignoredCount, List.copyOf(normalized));
    }

    private boolean shouldIngestRow(Map<?, ?> row, int index) {
        String publicationStatus = normalize(firstNonNull(row.get("publication_status"), row.get("publicationStatus")));
        if (publicationStatus.isBlank() || "published".equals(publicationStatus)) {
            return true;
        }
        if ("draft".equals(publicationStatus)) {
            return false;
        }
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "release manifest row #%d has invalid publication_status: %s".formatted(index + 1, publicationStatus)
        );
    }

    private Map<String, String> sanitizeStringMap(Map<?, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            if (!key.isBlank()) {
                out.put(key, normalize(entry.getValue()));
            }
        }
        return out;
    }

    private List<String> sanitizeStringList(List<?> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object value : source) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest contains a non-object settings/defaults block");
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<?> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest verification_hints must be an array");
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> readJsonObject(String rawPayload, String errorPrefix) {
        Object parsed;
        try {
            parsed = objectMapper.readValue(rawPayload, Object.class);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorPrefix + ": " + exception.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorPrefix + ": expected a JSON object");
        }
        return map;
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

    private Boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = normalize(value);
        if (text.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(text);
    }

    private <E extends Enum<E>> E enumValue(Object value, Class<E> type, E fallback, int index, String fieldName) {
        if (value == null) {
            return fallback;
        }
        String text = normalize(value);
        if (text.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "release manifest row #%d has invalid %s: %s".formatted(index + 1, fieldName, text)
            );
        }
    }

    private String releaseKey(String sourceRef, String sourceVersion) {
        return normalize(sourceRef) + "::" + normalize(sourceVersion);
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

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ParsedManifest(int manifestReleaseCount, int ignoredCount, List<ReleaseCreateRequest> requests) {}
}
