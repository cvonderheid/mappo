package com.mappo.pulumi;

import com.pulumi.azurenative.Provider;
import com.pulumi.azurenative.ProviderArgs;
import java.util.HashMap;
import java.util.Map;

final class AzureProviderRegistry {
    private final Map<String, Provider> providersBySubscription = new HashMap<>();

    Provider get(String subscriptionId) {
        return providersBySubscription.computeIfAbsent(
            subscriptionId,
            id -> new Provider(
                "provider-sub-" + PulumiSupport.subscriptionKey(id),
                ProviderArgs.builder().subscriptionId(id).build()
            )
        );
    }
}
