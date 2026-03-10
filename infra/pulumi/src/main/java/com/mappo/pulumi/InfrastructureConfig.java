package com.mappo.pulumi;

import com.pulumi.Config;
import com.pulumi.core.Output;

record InfrastructureConfig(
    FrontDoorConfig frontDoor,
    ControlPlanePostgresConfig controlPlanePostgres
) {
    static InfrastructureConfig load(Config config, String stackName) {
        String defaultLocation = config.get("defaultLocation").orElse("eastus");
        String controlPlaneSubscriptionId = PulumiSupport.optionalConfigWithEnvFallback(
            config,
            "controlPlaneSubscriptionId",
            "MAPPO_CONTROL_PLANE_SUBSCRIPTION_ID"
        ).orElse(PulumiSupport.resolveDemoSubscriptionId(null));
        String controlPlaneLocation = config.get("controlPlaneLocation").orElse(defaultLocation);
        Output<String> controlPlanePostgresAdminPassword = PulumiSupport.optionalSecretConfigWithEnvFallback(
            config,
            "controlPlanePostgresAdminPassword",
            "MAPPO_CONTROL_PLANE_DB_ADMIN_PASSWORD"
        ).orElse(null);

        if (PulumiSupport.booleanConfigWithEnvFallback(
            config,
            "controlPlanePostgresEnabled",
            "MAPPO_CONTROL_PLANE_DB_ENABLED",
            false
        ) && controlPlanePostgresAdminPassword == null) {
            throw new IllegalStateException(
                "Managed Postgres is enabled, but control plane DB admin password is missing. "
                    + "Set mappo:controlPlanePostgresAdminPassword (secret) or "
                    + "MAPPO_CONTROL_PLANE_DB_ADMIN_PASSWORD."
            );
        }

        FrontDoorConfig frontDoor = new FrontDoorConfig(
            PulumiSupport.booleanConfigWithEnvFallback(config, "frontDoorEnabled", "MAPPO_FRONT_DOOR_ENABLED", false),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorSubscriptionId", "MAPPO_FRONT_DOOR_SUBSCRIPTION_ID")
                .orElse(controlPlaneSubscriptionId),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorResourceGroupName", "MAPPO_FRONT_DOOR_RESOURCE_GROUP")
                .orElse("rg-mappo-edge-" + PulumiSupport.normalizeName(stackName, "demo", 16)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorResourceGroupLocation", "MAPPO_FRONT_DOOR_RESOURCE_GROUP_LOCATION")
                .orElse(controlPlaneLocation),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorProfileSku", "MAPPO_FRONT_DOOR_PROFILE_SKU")
                .orElse("Standard_AzureFrontDoor"),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorProfileName", "MAPPO_FRONT_DOOR_PROFILE_NAME")
                .orElse(PulumiSupport.normalizeName("afd-mappo-" + stackName, "afd-mappo-demo", 60)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorEndpointName", "MAPPO_FRONT_DOOR_ENDPOINT_NAME")
                .orElse(PulumiSupport.normalizeName("ep-mappo-" + stackName, "ep-mappo-demo", 46)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorOriginGroupName", "MAPPO_FRONT_DOOR_ORIGIN_GROUP_NAME")
                .orElse(PulumiSupport.normalizeName("og-mappo-api-" + stackName, "og-mappo-api-demo", 60)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorOriginName", "MAPPO_FRONT_DOOR_ORIGIN_NAME")
                .orElse(PulumiSupport.normalizeName("origin-mappo-api-" + stackName, "origin-mappo-api-demo", 60)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorRouteName", "MAPPO_FRONT_DOOR_ROUTE_NAME")
                .orElse(PulumiSupport.normalizeName("route-mappo-api-" + stackName, "route-mappo-api-demo", 60)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorOriginHost", "MAPPO_FRONT_DOOR_ORIGIN_HOST")
                .or(() -> PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorOriginUrl", "MAPPO_FRONT_DOOR_ORIGIN_URL").map(FrontDoorResources::extractHost))
                .or(() -> PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeBackendUrl", "MAPPO_RUNTIME_BACKEND_URL").map(FrontDoorResources::extractHost))
                .orElse(""),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorHealthProbePath", "MAPPO_FRONT_DOOR_HEALTH_PROBE_PATH")
                .orElse("/api/v1/health/live"),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorCustomDomainHostName", "MAPPO_FRONT_DOOR_CUSTOM_DOMAIN").orElse(null),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorDnsZoneSubscriptionId", "MAPPO_FRONT_DOOR_DNS_ZONE_SUBSCRIPTION_ID")
                .orElse(PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorSubscriptionId", "MAPPO_FRONT_DOOR_SUBSCRIPTION_ID")
                    .orElse(controlPlaneSubscriptionId)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorDnsZoneResourceGroupName", "MAPPO_FRONT_DOOR_DNS_ZONE_RESOURCE_GROUP").orElse(null),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontDoorDnsZoneName", "MAPPO_FRONT_DOOR_DNS_ZONE_NAME").orElse(null)
        );

        ControlPlanePostgresConfig controlPlanePostgres = new ControlPlanePostgresConfig(
            PulumiSupport.booleanConfigWithEnvFallback(config, "controlPlanePostgresEnabled", "MAPPO_CONTROL_PLANE_DB_ENABLED", false),
            controlPlaneSubscriptionId,
            controlPlaneLocation,
            config.get("controlPlaneResourceGroupPrefix").orElse("rg-mappo-control-plane"),
            config.get("controlPlanePostgresServerNamePrefix").orElse("pg-mappo"),
            config.get("controlPlanePostgresDatabaseName").orElse("mappo"),
            PulumiSupport.normalizePostgresLogin(
                PulumiSupport.optionalConfigWithEnvFallback(config, "controlPlanePostgresAdminLogin", "MAPPO_CONTROL_PLANE_DB_ADMIN_LOGIN")
                    .orElse("mappoadmin")
            ),
            config.get("controlPlanePostgresVersion").orElse("16"),
            config.get("controlPlanePostgresSkuName").orElse("Standard_B1ms"),
            PulumiSupport.numberConfigWithEnvFallback(config, "controlPlanePostgresStorageSizeGb", "MAPPO_CONTROL_PLANE_DB_STORAGE_GB", 32),
            PulumiSupport.numberConfigWithEnvFallback(config, "controlPlanePostgresBackupRetentionDays", "MAPPO_CONTROL_PLANE_DB_BACKUP_RETENTION_DAYS", 7),
            PulumiSupport.booleanConfigWithEnvFallback(config, "controlPlanePostgresPublicNetworkAccess", "MAPPO_CONTROL_PLANE_DB_PUBLIC_NETWORK_ACCESS", true),
            PulumiSupport.booleanConfigWithEnvFallback(config, "controlPlanePostgresAllowAzureServices", "MAPPO_CONTROL_PLANE_DB_ALLOW_AZURE_SERVICES", true),
            controlPlanePostgresAdminPassword,
            PulumiSupport.parseFirewallIpRanges(config, "controlPlanePostgresAllowedIpRanges", "MAPPO_CONTROL_PLANE_DB_ALLOWED_IPS")
        );

        return new InfrastructureConfig(frontDoor, controlPlanePostgres);
    }
}
