package com.mappo.controlplane.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record SecretReferenceLinkedReleaseSourceRecord(
    @Schema(description = "Release source id.")
    String id,
    @Schema(description = "Release source display name.")
    String name
) {
}
