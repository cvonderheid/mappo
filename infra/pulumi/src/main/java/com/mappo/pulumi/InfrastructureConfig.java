package com.mappo.pulumi;

import com.pulumi.Config;
import com.pulumi.core.Output;

record InfrastructureConfig(
    String stackKind,
    String platformStack,
    ControlPlanePostgresConfig controlPlanePostgres,
    RuntimeConfig runtime
) {
    static InfrastructureConfig load(Config config, String stackName) {
        String stackKind = PulumiSupport.optionalConfigWithEnvFallback(config, "stackKind", "MAPPO_PULUMI_STACK_KIND")
            .orElse("platform")
            .toLowerCase();
        if (!"platform".equals(stackKind) && !"runtime".equals(stackKind)) {
            throw new IllegalArgumentException("mappo:stackKind must be 'platform' or 'runtime'.");
        }
        String platformStack = PulumiSupport.optionalConfigWithEnvFallback(config, "platformStack", "MAPPO_PLATFORM_STACK")
            .orElse("");

        String defaultLocation = config.get("defaultLocation").orElse("eastus");
        String runtimeSubscriptionId = PulumiSupport.optionalConfigWithEnvFallback(
            config,
            "runtimeSubscriptionId",
            "MAPPO_RUNTIME_SUBSCRIPTION_ID"
        ).orElse(PulumiSupport.resolveDemoSubscriptionId(null));
        String runtimeLocation = PulumiSupport.optionalConfigWithEnvFallback(
            config,
            "runtimeLocation",
            "MAPPO_RUNTIME_LOCATION"
        ).orElse(defaultLocation);
        String azureTenantId = PulumiSupport.optionalConfigWithEnvFallback(config, "azureTenantId", "MAPPO_AZURE_TENANT_ID")
            .or(PulumiSupport::resolveActiveAzTenantId)
            .orElse("");
        String runtimeSuffix = PulumiSupport.stackScopedResourceSuffix(stackName, runtimeSubscriptionId);
        String compactResourceToken = PulumiSupport.normalizeCompactName(runtimeSuffix, PulumiSupport.subscriptionKey(runtimeSubscriptionId), 40);
        String runtimeResourceGroupName = PulumiSupport.optionalConfigWithEnvFallback(
            config,
            "runtimeResourceGroupName",
            "MAPPO_RUNTIME_RESOURCE_GROUP"
        ).orElse("rg-mappo-runtime-" + PulumiSupport.stackKey(stackName));

        String controlPlaneLocation = config.get("controlPlaneLocation").orElse(runtimeLocation);
        String controlPlaneResourceGroupName = PulumiSupport.optionalConfigWithEnvFallback(
            config,
            "controlPlaneResourceGroupName",
            "MAPPO_CONTROL_PLANE_RESOURCE_GROUP"
        ).orElse(runtimeResourceGroupName);
        Output<String> controlPlanePostgresAdminPassword = PulumiSupport.optionalSecretConfigWithEnvFallback(
            config,
            "controlPlanePostgresAdminPassword",
            "MAPPO_CONTROL_PLANE_DB_ADMIN_PASSWORD"
        ).orElse(null);

        ControlPlanePostgresConfig controlPlanePostgres = new ControlPlanePostgresConfig(
            PulumiSupport.booleanConfigWithEnvFallback(config, "controlPlanePostgresEnabled", "MAPPO_CONTROL_PLANE_DB_ENABLED", true),
            runtimeSuffix,
            controlPlaneLocation,
            controlPlaneResourceGroupName,
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

        RuntimeConfig runtime = new RuntimeConfig(
            PulumiSupport.booleanConfigWithEnvFallback(config, "runtimeEasyAuthEnabled", "MAPPO_EASYAUTH_ENABLED", true),
            runtimeSubscriptionId,
            runtimeSuffix,
            runtimeLocation,
            runtimeResourceGroupName,
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeContainerEnvironmentName", "MAPPO_RUNTIME_ENVIRONMENT")
                .orElse(PulumiSupport.normalizeName("cae-mappo-runtime-" + PulumiSupport.stackKey(stackName), "cae-mappo-runtime", 32)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeAcrName", "MAPPO_RUNTIME_ACR_NAME")
                .orElse(PulumiSupport.normalizeCompactName("acrmappo" + compactResourceToken, "acrmappo", 50)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeKeyVaultName", "MAPPO_RUNTIME_KEY_VAULT_NAME")
                .orElse(PulumiSupport.normalizeCompactName("kvmappo" + compactResourceToken, "kvmappo", 24)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeRedisName", "MAPPO_RUNTIME_REDIS_NAME")
                .orElse(PulumiSupport.normalizeName("redis-mappo-" + PulumiSupport.stackKey(stackName), "redis-mappo", 63)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeManagedIdentityName", "MAPPO_RUNTIME_MANAGED_IDENTITY_NAME")
                .orElse(PulumiSupport.normalizeName("mi-mappo-runtime-" + PulumiSupport.stackKey(stackName), "mi-mappo-runtime", 128)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeBackendAppName", "MAPPO_RUNTIME_BACKEND_APP")
                .orElse(PulumiSupport.normalizeName("ca-mappo-api-" + PulumiSupport.stackKey(stackName), "ca-mappo-api", 32)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeFrontendAppName", "MAPPO_RUNTIME_FRONTEND_APP")
                .orElse(PulumiSupport.normalizeName("ca-mappo-ui-" + PulumiSupport.stackKey(stackName), "ca-mappo-ui", 32)),
            PulumiSupport.optionalConfigWithEnvFallback(config, "imageTag", "MAPPO_RUNTIME_IMAGE_TAG").orElse("1.0.0-SNAPSHOT-nogit"),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeCorsOrigins", "MAPPO_CORS_ORIGINS")
                .orElse("http://localhost:5174,http://127.0.0.1:5174"),
            PulumiSupport.numberConfigWithEnvFallback(config, "runtimeMinReplicas", "MAPPO_RUNTIME_MIN_REPLICAS", 1),
            PulumiSupport.numberConfigWithEnvFallback(config, "runtimeMaxReplicas", "MAPPO_RUNTIME_MAX_REPLICAS", 3),
            PulumiSupport.doubleConfigWithEnvFallback(config, "runtimeBackendCpu", "MAPPO_RUNTIME_BACKEND_CPU", 1.0),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeBackendMemory", "MAPPO_RUNTIME_BACKEND_MEMORY").orElse("2.0Gi"),
            PulumiSupport.doubleConfigWithEnvFallback(config, "runtimeFrontendCpu", "MAPPO_RUNTIME_FRONTEND_CPU", 0.5),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeFrontendMemory", "MAPPO_RUNTIME_FRONTEND_MEMORY").orElse("1.0Gi"),
            PulumiSupport.doubleConfigWithEnvFallback(config, "runtimeMigrationCpu", "MAPPO_RUNTIME_MIGRATION_CPU", 0.5),
            PulumiSupport.optionalConfigWithEnvFallback(config, "runtimeMigrationMemory", "MAPPO_RUNTIME_MIGRATION_MEMORY").orElse("1.0Gi"),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontendCustomDomain", "MAPPO_FRONTEND_CUSTOM_DOMAIN").orElse(""),
            PulumiSupport.optionalConfigWithEnvFallback(config, "backendCustomDomain", "MAPPO_BACKEND_CUSTOM_DOMAIN").orElse(""),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontendDnsZoneName", "MAPPO_FRONTEND_DNS_ZONE_NAME").orElse(""),
            PulumiSupport.optionalConfigWithEnvFallback(config, "frontendDnsZoneResourceGroup", "MAPPO_FRONTEND_DNS_ZONE_RESOURCE_GROUP").orElse(""),
            PulumiSupport.booleanConfigWithEnvFallback(
                config,
                "frontendCustomDomainCertificateEnabled",
                "MAPPO_FRONTEND_CUSTOM_DOMAIN_CERTIFICATE_ENABLED",
                false
            ),
            PulumiSupport.booleanConfigWithEnvFallback(
                config,
                "backendCustomDomainCertificateEnabled",
                "MAPPO_BACKEND_CUSTOM_DOMAIN_CERTIFICATE_ENABLED",
                false
            ),
            azureTenantId,
            PulumiSupport.optionalConfigWithEnvFallback(config, "keyVaultAccessObjectId", "MAPPO_AZURE_KEY_VAULT_ACCESS_OBJECT_ID").orElse(""),
            PulumiSupport.optionalConfigWithEnvFallback(config, "azureTenantBySubscription", "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION").orElse(""),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "marketplaceIngestToken", "MAPPO_MARKETPLACE_INGEST_TOKEN").orElse(null),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "publisherAcrServer", "MAPPO_PUBLISHER_ACR_SERVER").orElse(null),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "publisherAcrPullClientId", "MAPPO_PUBLISHER_ACR_PULL_CLIENT_ID").orElse(null),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "publisherAcrPullSecretName", "MAPPO_PUBLISHER_ACR_PULL_SECRET_NAME").orElse(null),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "publisherAcrPullClientSecret", "MAPPO_PUBLISHER_ACR_PULL_CLIENT_SECRET").orElse(null),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "githubReleaseWebhookSecret", "MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET").orElse(null),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "githubReleaseToken", "MAPPO_MANAGED_APP_RELEASE_GITHUB_TOKEN").orElse(null),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "azureDevOpsPat", "MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN").orElse(null),
            PulumiSupport.optionalSecretConfigWithEnvFallback(config, "azureDevOpsWebhookSecret", "MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET").orElse(null)
        );

        if (runtime.tenantId().isBlank()) {
            throw new IllegalStateException(
                "Pulumi stacks require an Azure tenant. Set mappo:azureTenantId/MAPPO_AZURE_TENANT_ID or run az login."
            );
        }
        if ("runtime".equals(stackKind)) {
            if (platformStack.isBlank()) {
                throw new IllegalStateException("Runtime stack requires mappo:platformStack or MAPPO_PLATFORM_STACK.");
            }
            if (runtime.marketplaceIngestToken() == null) {
                throw new IllegalStateException("Runtime stack requires mappo:marketplaceIngestToken or MAPPO_MARKETPLACE_INGEST_TOKEN.");
            }
        }

        return new InfrastructureConfig(stackKind, platformStack, controlPlanePostgres, runtime);
    }
}
