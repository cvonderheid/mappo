package com.mappo.controlplane.model;

import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record ProviderConnectionRecord(
    @Schema(description = "Provider connection id.", example = "ado-default")
    String id,
    @Schema(description = "Provider connection display name.", example = "Azure DevOps Default Connection")
    String name,
    @Schema(description = "Provider type for this connection.")
    ProviderConnectionProviderType provider,
    @Schema(description = "Whether this provider connection is enabled.")
    boolean enabled,
    @Schema(description = "Optional organization scope filter.", example = "https://dev.azure.com/pg123")
    String organizationFilter,
    @Schema(
        description = "Secret reference for API credential lookup.",
        example = "mappo.azure-devops.personal-access-token"
    )
    String personalAccessTokenRef,
    @Schema(description = "Projects currently linked to this provider connection.")
    List<ProviderConnectionLinkedProjectRecord> linkedProjects,
    @Schema(description = "Created timestamp (UTC).")
    OffsetDateTime createdAt,
    @Schema(description = "Last updated timestamp (UTC).")
    OffsetDateTime updatedAt
) {
}
