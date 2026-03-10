package com.mappo.controlplane.azure;

import com.azure.core.management.profile.AzureProfile;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.mappo.controlplane.config.MappoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureExecutorClient {

    private final MappoProperties properties;

    public boolean isConfigured() {
        return !blank(properties.getAzure().getTenantId())
            && !blank(properties.getAzure().getClientId())
            && !blank(properties.getAzure().getClientSecret());
    }

    public AzureResourceManager createManager(String tenantId, String subscriptionId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzure().getTenantId() : tenantId;
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Azure SDK is not configured. Set MAPPO_AZURE_TENANT_ID, MAPPO_AZURE_CLIENT_ID, MAPPO_AZURE_CLIENT_SECRET."
            );
        }

        ClientSecretCredential credential = createCredential(effectiveTenant);
        AzureProfile profile = createProfile(effectiveTenant, subscriptionId);
        return AzureResourceManager.authenticate(credential, profile).withSubscription(subscriptionId);
    }

    public TokenCredential createTokenCredential(String tenantId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzure().getTenantId() : tenantId;
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Azure SDK is not configured. Set MAPPO_AZURE_TENANT_ID, MAPPO_AZURE_CLIENT_ID, MAPPO_AZURE_CLIENT_SECRET."
            );
        }
        return createCredential(effectiveTenant);
    }

    public ContainerAppsApiManager createContainerAppsManager(String tenantId, String subscriptionId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzure().getTenantId() : tenantId;
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Azure SDK is not configured. Set MAPPO_AZURE_TENANT_ID, MAPPO_AZURE_CLIENT_ID, MAPPO_AZURE_CLIENT_SECRET."
            );
        }
        return ContainerAppsApiManager.authenticate(createCredential(effectiveTenant), createProfile(effectiveTenant, subscriptionId));
    }

    public ResourceManager createResourceManager(String tenantId, String subscriptionId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzure().getTenantId() : tenantId;
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Azure SDK is not configured. Set MAPPO_AZURE_TENANT_ID, MAPPO_AZURE_CLIENT_ID, MAPPO_AZURE_CLIENT_SECRET."
            );
        }
        return ResourceManager.authenticate(createCredential(effectiveTenant), createProfile(effectiveTenant, subscriptionId))
            .withSubscription(subscriptionId);
    }

    private ClientSecretCredential createCredential(String tenantId) {
        return new ClientSecretCredentialBuilder()
            .tenantId(tenantId)
            .clientId(properties.getAzure().getClientId())
            .clientSecret(properties.getAzure().getClientSecret())
            .build();
    }

    private AzureProfile createProfile(String tenantId, String subscriptionId) {
        return new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
