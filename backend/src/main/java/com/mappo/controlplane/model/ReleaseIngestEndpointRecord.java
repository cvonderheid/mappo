package com.mappo.controlplane.model;

import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record ReleaseIngestEndpointRecord(
    @Schema(description = "Release ingest endpoint id.", example = "github-managed-app-default")
    String id,
    @Schema(description = "Release ingest endpoint display name.", example = "GitHub Managed App Default")
    String name,
    @Schema(description = "Webhook provider for this endpoint.")
    ReleaseIngestProviderType provider,
    @Schema(description = "Whether this endpoint is active for webhook processing.")
    boolean enabled,
    @Schema(description = "Secret reference used for webhook authentication.", example = "mappo.managed-app-release.webhook-secret")
    String secretRef,
    @Schema(description = "Optional provider repository filter.", example = "cvonderheid/mappo-managed-app")
    String repoFilter,
    @Schema(description = "Optional branch filter.", example = "main")
    String branchFilter,
    @Schema(description = "Optional pipeline id filter (ADO).", example = "42")
    String pipelineIdFilter,
    @Schema(description = "Optional manifest path filter for release files.", example = "releases/releases.manifest.json")
    String manifestPath,
    @Schema(description = "Projects currently linked to this release ingest endpoint.")
    List<ReleaseIngestLinkedProjectRecord> linkedProjects,
    @Schema(description = "Created timestamp (UTC).")
    OffsetDateTime createdAt,
    @Schema(description = "Last updated timestamp (UTC).")
    OffsetDateTime updatedAt
) {
}
