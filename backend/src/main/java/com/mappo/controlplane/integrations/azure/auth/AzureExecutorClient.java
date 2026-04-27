package com.mappo.controlplane.integrations.azure.auth;

import com.azure.core.management.profile.AzureProfile;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
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
        return !blank(properties.getAzure().getTenantId());
    }

    public AzureResourceManager createManager(String tenantId, String subscriptionId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzure().getTenantId() : tenantId;
        if (!isConfigured()) {
            throw notConfigured();
        }

        TokenCredential credential = createCredential(effectiveTenant);
        AzureProfile profile = createProfile(effectiveTenant, subscriptionId);
        return AzureResourceManager.authenticate(credential, profile).withSubscription(subscriptionId);
    }

    public TokenCredential createTokenCredential(String tenantId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzure().getTenantId() : tenantId;
        if (!isConfigured()) {
            throw notConfigured();
        }
        return createCredential(effectiveTenant);
    }

    public ContainerAppsApiManager createContainerAppsManager(String tenantId, String subscriptionId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzure().getTenantId() : tenantId;
        if (!isConfigured()) {
            throw notConfigured();
        }
        return ContainerAppsApiManager.authenticate(createCredential(effectiveTenant), createProfile(effectiveTenant, subscriptionId));
    }

    public ResourceManager createResourceManager(String tenantId, String subscriptionId) {
        String effectiveTenant = blank(tenantId) ? properties.getAzure().getTenantId() : tenantId;
        if (!isConfigured()) {
            throw notConfigured();
        }
        return ResourceManager.authenticate(createCredential(effectiveTenant), createProfile(effectiveTenant, subscriptionId))
            .withSubscription(subscriptionId);
    }

    private TokenCredential createCredential(String tenantId) {
        DefaultAzureCredentialBuilder builder = new DefaultAzureCredentialBuilder()
            .tenantId(tenantId);
        if (!blank(properties.getAzure().getManagedIdentityClientId())) {
            builder.managedIdentityClientId(properties.getAzure().getManagedIdentityClientId());
        }
        return builder.build();
    }

    private AzureProfile createProfile(String tenantId, String subscriptionId) {
        return new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private IllegalStateException notConfigured() {
        return new IllegalStateException(
            "Azure SDK is not configured. Set MAPPO_AZURE_TENANT_ID or run in an Azure-hosted runtime with managed identity."
        );
    }
}
