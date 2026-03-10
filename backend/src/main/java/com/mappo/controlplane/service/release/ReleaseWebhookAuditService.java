package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.command.ReleaseWebhookDeliveryCommand;
import com.mappo.controlplane.repository.ReleaseWebhookRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReleaseWebhookAuditService {

    private final ReleaseWebhookRepository releaseWebhookRepository;
    private final LiveUpdateService liveUpdateService;

    public ReleaseWebhookAuditService(
        ReleaseWebhookRepository releaseWebhookRepository,
        LiveUpdateService liveUpdateService
    ) {
        this.releaseWebhookRepository = releaseWebhookRepository;
        this.liveUpdateService = liveUpdateService;
    }

    public String newDeliveryLogId(String externalDeliveryId, String eventType, String payload, OffsetDateTime receivedAt) {
        String basis = firstNonBlank(normalize(externalDeliveryId), normalize(eventType), "github")
            + "::" + normalize(payload)
            + "::" + receivedAt.toInstant().toEpochMilli();
        return "rwh-" + sha256Hex(basis).substring(0, 12);
    }

    public void logWebhookDelivery(
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
