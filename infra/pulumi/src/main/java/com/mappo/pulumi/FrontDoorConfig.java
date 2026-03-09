package com.mappo.pulumi;

record FrontDoorConfig(
    boolean enabled,
    String subscriptionId,
    String resourceGroupName,
    String resourceGroupLocation,
    String profileSku,
    String profileName,
    String endpointName,
    String originGroupName,
    String originName,
    String routeName,
    String originHost,
    String healthProbePath,
    String customDomainHostName,
    String dnsZoneSubscriptionId,
    String dnsZoneResourceGroupName,
    String dnsZoneName
) {
    boolean hasCustomDomain() {
        return customDomainHostName != null && !customDomainHostName.isBlank();
    }

    boolean hasAzureDnsZone() {
        return dnsZoneResourceGroupName != null && !dnsZoneResourceGroupName.isBlank()
            && dnsZoneName != null && !dnsZoneName.isBlank();
    }
}
