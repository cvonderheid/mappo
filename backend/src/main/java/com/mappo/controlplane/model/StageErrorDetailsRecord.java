package com.mappo.controlplane.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StageErrorDetailsRecord(
    Integer statusCode,
    String error,
    String desiredImage,
    String azureErrorCode,
    String azureErrorMessage,
    String azureRequestId,
    String azureArmServiceRequestId,
    String azureCorrelationId,
    String azureDeploymentName,
    String azureOperationId,
    String azureResourceId
) {
}
