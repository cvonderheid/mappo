package com.mappo.controlplane.model;

import java.util.List;

public record ReleaseManifestIngestResultRecord(
    String repo,
    String path,
    String ref,
    int manifestReleaseCount,
    int createdCount,
    int skippedCount,
    int ignoredCount,
    List<String> createdReleaseIds
) {
}
