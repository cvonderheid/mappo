package com.mappo.controlplane.integrations.github.release;

import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;

record GithubWebhookDecision(
    boolean processManifest,
    MappoReleaseWebhookStatus status,
    String message
) {
}
