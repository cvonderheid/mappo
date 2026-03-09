package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record ReleaseWebhookDeliveryRecord(
    String id,
    String externalDeliveryId,
    String eventType,
    String repo,
    String ref,
    String manifestPath,
    MappoReleaseWebhookStatus status,
    String message,
    List<String> changedPaths,
    Integer manifestReleaseCount,
    Integer createdCount,
    Integer skippedCount,
    Integer ignoredCount,
    List<String> createdReleaseIds,
    OffsetDateTime receivedAt
) {
}
