package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record ReleaseWebhookDeliveryCommand(
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
    List<String> projectIds,
    OffsetDateTime receivedAt
) {
}
