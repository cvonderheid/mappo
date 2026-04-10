package com.mappo.controlplane.integrations.github.release;

import java.util.List;

public record GithubWebhookPayloadRecord(
    String repo,
    String ref,
    List<String> changedPaths,
    boolean touchesManifestPath
) {
}
