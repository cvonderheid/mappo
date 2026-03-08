package com.mappo.controlplane.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TargetRegistrationMetadataRecord(
    String containerAppName,
    String source,
    String deploymentStackName,
    MappoRegistryAuthMode registryAuthMode,
    String registryServer,
    String registryUsername,
    String registryPasswordSecretName
) {
}
