package com.mappo.controlplane.service.release;

import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;

record GithubWebhookDecision(
    boolean processManifest,
    MappoReleaseWebhookStatus status,
    String message
) {
}
