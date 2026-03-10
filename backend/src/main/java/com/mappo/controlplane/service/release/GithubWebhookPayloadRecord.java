package com.mappo.controlplane.service.release;

import java.util.List;

public record GithubWebhookPayloadRecord(
    String repo,
    String ref,
    List<String> changedPaths,
    boolean touchesManifestPath
) {
}
