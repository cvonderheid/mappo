package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TargetRegistrationMetadataRequest(
    String containerAppName,
    String source,
    String deploymentStackName,
    MappoRegistryAuthMode registryAuthMode,
    String registryServer,
    String registryUsername,
    String registryPasswordSecretName
) {
}
