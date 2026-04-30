package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Typed release-artifact-source configuration. The selected releaseArtifactSource determines which fields are used.")
public record ProjectReleaseArtifactSourceConfigRequest(
    String descriptor,
    String templateUriField,
    String versionRefField,
    String sourceSystem,
    String descriptorPath,
    String versionField
) {
}
