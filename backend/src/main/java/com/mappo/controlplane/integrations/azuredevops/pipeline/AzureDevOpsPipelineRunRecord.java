package com.mappo.controlplane.integrations.azuredevops.pipeline;

public record AzureDevOpsPipelineRunRecord(
    String runId,
    String runName,
    String state,
    String result,
    String webUrl,
    String logsUrl,
    String apiUrl
) {

    public boolean terminal() {
        String normalizedState = normalize(state);
        if ("completed".equals(normalizedState) || "cancelled".equals(normalizedState)) {
            return true;
        }
        return !normalize(result).isBlank();
    }

    public boolean succeeded() {
        return "succeeded".equals(normalize(result));
    }

    public String executionStatus() {
        String normalizedResult = normalize(result);
        if (!normalizedResult.isBlank()) {
            return normalizedResult;
        }
        return normalize(state);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
