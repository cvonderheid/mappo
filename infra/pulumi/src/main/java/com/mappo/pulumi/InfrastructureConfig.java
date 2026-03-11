package com.mappo.pulumi;

import com.pulumi.Config;
import com.pulumi.core.Output;

record InfrastructureConfig(
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

        return new InfrastructureConfig(controlPlanePostgres);
    }
}
