package com.mappo.controlplane.integrations.azuredevops.connection;

import com.mappo.controlplane.application.providerconnection.ProviderConnectionProviderDescriptor;
import com.mappo.controlplane.application.providerconnection.ProviderConnectionDefaultSecretReferences;
import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;
import com.mappo.controlplane.integrations.azuredevops.common.AzureDevOpsUrlNormalizer;
import org.springframework.stereotype.Component;

@Component
public class AzureDevOpsProviderConnectionProviderDescriptor implements ProviderConnectionProviderDescriptor {

    @Override
    public ProviderConnectionProviderType provider() {
        return ProviderConnectionProviderType.azure_devops;
    }

    @Override
    public String normalizeOrganizationUrl(String value) {
        return AzureDevOpsUrlNormalizer.normalizeOrganizationUrl(value, "https://dev.azure.com");
    }

    @Override
    public String defaultPersonalAccessTokenRef() {
        return ProviderConnectionDefaultSecretReferences.AZURE_DEVOPS_PAT_SECRET_REF;
    }

    @Override
    public SecretReferenceProviderType secretReferenceProvider() {
        return SecretReferenceProviderType.azure_devops;
    }

    @Override
    public SecretReferenceUsageType personalAccessTokenUsage() {
        return SecretReferenceUsageType.deployment_api_credential;
    }
}
