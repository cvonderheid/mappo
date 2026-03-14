package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfigurationPatchRequest(
    String name,
    Map<String, Object> accessStrategyConfig,
    Map<String, Object> deploymentDriverConfig,
    Map<String, Object> releaseArtifactSourceConfig,
    Map<String, Object> runtimeHealthProviderConfig
) {
}
