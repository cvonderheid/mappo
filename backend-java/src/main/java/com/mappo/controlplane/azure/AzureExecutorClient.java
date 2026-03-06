package com.mappo.controlplane.azure;

import com.azure.core.management.profile.AzureProfile;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.mappo.controlplane.config.MappoProperties;
import org.springframework.stereotype.Component;

@Component
public class AzureExecutorClient {

    private final MappoProperties properties;

    public AzureExecutorClient(MappoProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return !blank(properties.getAzureTenantId())
            && !blank(properties.getAzureClientId())
            && !blank(properties.getAzureClientSecret());
    }

    public AzureResourceManager createManager(String tenantId, String subscriptionId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzureTenantId() : tenantId;
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Azure SDK is not configured. Set MAPPO_AZURE_TENANT_ID, MAPPO_AZURE_CLIENT_ID, MAPPO_AZURE_CLIENT_SECRET."
            );
        }

        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
            .tenantId(effectiveTenant)
            .clientId(properties.getAzureClientId())
            .clientSecret(properties.getAzureClientSecret())
            .build();

        AzureProfile profile = new AzureProfile(effectiveTenant, subscriptionId, AzureEnvironment.AZURE);
        return AzureResourceManager.authenticate(credential, profile).withSubscription(subscriptionId);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
