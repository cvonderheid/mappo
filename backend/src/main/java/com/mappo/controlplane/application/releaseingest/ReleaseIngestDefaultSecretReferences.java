package com.mappo.controlplane.application.releaseingest;

public final class ReleaseIngestDefaultSecretReferences {

    public static final String AZURE_DEVOPS_WEBHOOK_SECRET_REF = "mappo.azure-devops.webhook-secret";
    public static final String GITHUB_WEBHOOK_SECRET_REF = "mappo.managed-app-release.webhook-secret";

    private ReleaseIngestDefaultSecretReferences() {
    }
}
