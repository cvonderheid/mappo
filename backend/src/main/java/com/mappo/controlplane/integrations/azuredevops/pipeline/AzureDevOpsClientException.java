package com.mappo.controlplane.integrations.azuredevops.pipeline;

public class AzureDevOpsClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public AzureDevOpsClientException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody.trim();
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}
