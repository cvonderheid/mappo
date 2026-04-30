package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Typed deployment-driver configuration. The selected deploymentDriver determines which fields are used.")
public record ProjectDeploymentDriverConfigRequest(
    Boolean supportsPreview,
    String previewMode,
    Boolean supportsExternalExecutionHandle,
    String pipelineSystem,
    String organization,
    String project,
    String repository,
    String pipelineId,
    String branch,
    Boolean supportsExternalLogs
) {
}
