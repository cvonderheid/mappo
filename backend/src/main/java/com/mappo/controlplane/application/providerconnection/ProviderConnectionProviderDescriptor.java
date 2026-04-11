package com.mappo.controlplane.application.providerconnection;

import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;

public interface ProviderConnectionProviderDescriptor {

    ProviderConnectionProviderType provider();

    String normalizeOrganizationUrl(String value);

    String defaultPersonalAccessTokenRef();

    SecretReferenceProviderType secretReferenceProvider();

    SecretReferenceUsageType personalAccessTokenUsage();
}
