package com.mappo.controlplane.model;

import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record ProviderConnectionRecord(
    @Schema(description = "Deployment connection id.", example = "ado-default")
    String id,
    @Schema(description = "Deployment connection display name.", example = "Azure DevOps Default Connection")
    String name,
    @Schema(description = "External system this deployment connection talks to.")
    ProviderConnectionProviderType provider,
    @Schema(description = "Whether this deployment connection is enabled.")
    boolean enabled,
    @Schema(description = "Verified Azure DevOps account URL used for project discovery.", example = "https://dev.azure.com/example-ado-org")
    String organizationUrl,
    @Schema(
        description = "Secret reference used to resolve the external-system API credential.",
        example = "mappo.azure-devops.personal-access-token"
    )
    String personalAccessTokenRef,
    @Schema(description = "Azure DevOps projects MAPPO discovered and cached for this deployment connection.")
    List<ProviderConnectionAdoProjectRecord> discoveredProjects,
    @Schema(description = "Projects currently using this deployment connection.")
    List<ProviderConnectionLinkedProjectRecord> linkedProjects,
    @Schema(description = "Created timestamp (UTC).")
    OffsetDateTime createdAt,
    @Schema(description = "Last updated timestamp (UTC).")
    OffsetDateTime updatedAt
) {
}
