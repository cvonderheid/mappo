package com.mappo.controlplane.model;

import com.mappo.controlplane.domain.secretreference.SecretReferenceModeType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record SecretReferenceRecord(
    @Schema(description = "Secret reference id.", example = "ado-runtime-pat")
    String id,
    @Schema(description = "Secret reference display name.", example = "Azure DevOps Runtime PAT")
    String name,
    @Schema(description = "External provider this secret is intended for.")
    SecretReferenceProviderType provider,
    @Schema(description = "How this secret is used by MAPPO.")
    SecretReferenceUsageType usage,
    @Schema(description = "Where MAPPO resolves this secret from.")
    SecretReferenceModeType mode,
    @Schema(description = "Normalized backend secret reference MAPPO resolves at runtime.", example = "kv:mappo-ado-org-pat")
    String backendRef,
    @Schema(description = "Deployment connections currently using this secret reference.")
    List<SecretReferenceLinkedDeploymentConnectionRecord> linkedDeploymentConnections,
    @Schema(description = "Release sources currently using this secret reference.")
    List<SecretReferenceLinkedReleaseSourceRecord> linkedReleaseSources,
    @Schema(description = "Created timestamp (UTC).")
    OffsetDateTime createdAt,
    @Schema(description = "Last updated timestamp (UTC).")
    OffsetDateTime updatedAt
) {
}
