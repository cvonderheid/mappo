package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Typed access-strategy configuration. The selected accessStrategy determines which fields are used.")
public record ProjectAccessStrategyConfigRequest(
    String authModel,
    Boolean requiresAzureCredential,
    Boolean requiresTargetExecutionMetadata,
    String azureServiceConnectionName,
    String managingTenantId,
    String managingPrincipalClientId,
    Boolean requiresDelegation
) {
}
