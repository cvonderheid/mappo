package com.mappo.controlplane.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TargetRegistrationMetadataRecord(
    String containerAppName,
    String source,
    String deploymentStackName,
    MappoRegistryAuthMode registryAuthMode,
    String registryServer,
    String registryUsername,
    String registryPasswordSecretName,
    Map<String, String> executionConfig
) {
}
