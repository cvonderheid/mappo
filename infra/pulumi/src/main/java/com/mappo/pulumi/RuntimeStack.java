package com.mappo.pulumi;

import com.pulumi.azuread.ApplicationPassword;
import com.pulumi.azuread.ApplicationPasswordArgs;
import com.pulumi.azuread.ApplicationRedirectUris;
import com.pulumi.azuread.ApplicationRedirectUrisArgs;
import com.pulumi.azuread.ApplicationRegistration;
import com.pulumi.azuread.ApplicationRegistrationArgs;
import com.pulumi.azuread.ServicePrincipal;
import com.pulumi.azuread.ServicePrincipalArgs;
import com.pulumi.azurenative.Provider;
import com.pulumi.azurenative.app.ContainerApp;
import com.pulumi.azurenative.app.ContainerAppArgs;
import com.pulumi.azurenative.app.ContainerAppsAuthConfig;
import com.pulumi.azurenative.app.ContainerAppsAuthConfigArgs;
import com.pulumi.azurenative.app.ManagedEnvironment;
import com.pulumi.azurenative.app.ManagedEnvironmentArgs;
import com.pulumi.azurenative.app.enums.ManagedServiceIdentityType;
import com.pulumi.azurenative.app.enums.UnauthenticatedClientActionV2;
import com.pulumi.azurenative.app.inputs.AuthPlatformArgs;
import com.pulumi.azurenative.app.inputs.AzureActiveDirectoryArgs;
import com.pulumi.azurenative.app.inputs.AzureActiveDirectoryRegistrationArgs;
import com.pulumi.azurenative.app.inputs.AzureActiveDirectoryValidationArgs;
import com.pulumi.azurenative.app.inputs.ConfigurationArgs;
import com.pulumi.azurenative.app.inputs.ContainerArgs;
import com.pulumi.azurenative.app.inputs.ContainerResourcesArgs;
import com.pulumi.azurenative.app.inputs.EnvironmentVarArgs;
import com.pulumi.azurenative.app.inputs.GlobalValidationArgs;
import com.pulumi.azurenative.app.inputs.IdentityProvidersArgs;
import com.pulumi.azurenative.app.inputs.IngressArgs;
import com.pulumi.azurenative.app.inputs.InitContainerArgs;
import com.pulumi.azurenative.app.inputs.ManagedServiceIdentityArgs;
import com.pulumi.azurenative.app.inputs.RegistryCredentialsArgs;
import com.pulumi.azurenative.app.inputs.ScaleArgs;
import com.pulumi.azurenative.app.inputs.SecretArgs;
import com.pulumi.azurenative.app.inputs.TemplateArgs;
import com.pulumi.azurenative.app.inputs.TrafficWeightArgs;
import com.pulumi.azurenative.containerregistry.ContainerregistryFunctions;
import com.pulumi.azurenative.containerregistry.Registry;
import com.pulumi.azurenative.containerregistry.RegistryArgs;
import com.pulumi.azurenative.containerregistry.inputs.ListRegistryCredentialsArgs;
import com.pulumi.azurenative.keyvault.Secret;
import com.pulumi.azurenative.keyvault.Vault;
import com.pulumi.azurenative.keyvault.VaultArgs;
import com.pulumi.azurenative.keyvault.enums.SkuName;
import com.pulumi.azurenative.keyvault.enums.SecretPermissions;
import com.pulumi.azurenative.keyvault.inputs.AccessPolicyEntryArgs;
import com.pulumi.azurenative.keyvault.inputs.PermissionsArgs;
import com.pulumi.azurenative.keyvault.inputs.SecretPropertiesArgs;
import com.pulumi.azurenative.keyvault.inputs.SkuArgs;
import com.pulumi.azurenative.keyvault.inputs.VaultPropertiesArgs;
import com.pulumi.azurenative.managedidentity.UserAssignedIdentity;
import com.pulumi.azurenative.managedidentity.UserAssignedIdentityArgs;
import com.pulumi.azurenative.redis.Redis;
import com.pulumi.azurenative.redis.RedisArgs;
import com.pulumi.azurenative.redis.RedisFunctions;
import com.pulumi.azurenative.redis.inputs.ListRedisKeysArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.pulumi.core.Output;
import com.pulumi.core.Either;
import com.pulumi.resources.CustomResourceOptions;
import java.util.ArrayList;
import java.util.List;

final class RuntimeStack {
    private RuntimeStack() {
    }

    static RuntimeResources create(RuntimeConfig config, ControlPlanePostgresResources postgres, Provider provider) {
        if (!config.enabled()) {
            return null;
        }

        String suffix = config.resourceNameSuffix();
        CustomResourceOptions withProvider = CustomResourceOptions.builder().provider(provider).build();

        Output<String> resourceGroupName;
        if (postgres == null) {
            ResourceGroup resourceGroup = new ResourceGroup(
                "runtime-rg-" + suffix,
                ResourceGroupArgs.builder()
                    .resourceGroupName(config.resourceGroupName())
                    .location(config.location())
                    .tags(PulumiSupport.linkedMapOfString(
                        "managedBy", "pulumi",
                        "system", "mappo",
                        "scope", "runtime"
                    ))
                    .build(),
                withProvider
            );
            resourceGroupName = resourceGroup.name();
        } else {
            resourceGroupName = postgres.resourceGroupName();
        }

        ManagedEnvironment environment = new ManagedEnvironment(
            "runtime-container-env-" + suffix,
            ManagedEnvironmentArgs.builder()
                .environmentName(config.containerEnvironmentName())
                .resourceGroupName(resourceGroupName)
                .location(config.location())
                .tags(PulumiSupport.linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "runtime-container-apps"
                ))
                .build(),
            withProvider
        );

        Registry registry = new Registry(
            "runtime-acr-" + suffix,
            RegistryArgs.builder()
                .registryName(config.acrName())
                .resourceGroupName(resourceGroupName)
                .location(config.location())
                .sku(com.pulumi.azurenative.containerregistry.inputs.SkuArgs.builder().name("Basic").build())
                .adminUserEnabled(true)
                .tags(PulumiSupport.linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "runtime-acr"
                ))
                .build(),
            withProvider
        );

        UserAssignedIdentity managedIdentity = new UserAssignedIdentity(
            "runtime-identity-" + suffix,
            UserAssignedIdentityArgs.builder()
                .resourceName(config.managedIdentityName())
                .resourceGroupName(resourceGroupName)
                .location(config.location())
                .tags(PulumiSupport.linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "runtime-identity"
                ))
                .build(),
            withProvider
        );

        Vault keyVault = new Vault(
            "runtime-key-vault-" + suffix,
            VaultArgs.builder()
                .vaultName(config.keyVaultName())
                .resourceGroupName(resourceGroupName)
                .location(config.location())
                .properties(VaultPropertiesArgs.builder()
                    .tenantId(config.tenantId())
                    .enableRbacAuthorization(false)
                    .enableSoftDelete(true)
                    .softDeleteRetentionInDays(7)
                    .sku(SkuArgs.builder().family("A").name(SkuName.Standard).build())
                    .accessPolicies(keyVaultAccessPolicies(config, managedIdentity))
                    .build())
                .tags(PulumiSupport.linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "runtime-secrets"
                ))
                .build(),
            withProvider
        );

        Redis redis = new Redis(
            "runtime-redis-" + suffix,
            RedisArgs.builder()
                .name(config.redisName())
                .resourceGroupName(resourceGroupName)
                .location(config.location())
                .sku(com.pulumi.azurenative.redis.inputs.SkuArgs.builder()
                    .name("Basic")
                    .family("C")
                    .capacity(0)
                    .build())
                .minimumTlsVersion("1.2")
                .enableNonSslPort(false)
                .publicNetworkAccess("Enabled")
                .disableAccessKeyAuthentication(false)
                .tags(PulumiSupport.linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "runtime-redis"
                ))
                .build(),
            withProvider
        );

        Output<String> redisPassword = RedisFunctions.listRedisKeys(ListRedisKeysArgs.builder()
                .name(redis.name())
                .resourceGroupName(resourceGroupName)
                .build())
            .applyValue(keys -> keys.primaryKey());

        writeKeyVaultSecret("runtime-db-password-" + suffix, keyVault, resourceGroupName, "mappo-db-admin-password", postgres == null ? null : postgres.password(), withProvider);
        writeKeyVaultSecret("runtime-redis-password-" + suffix, keyVault, resourceGroupName, "mappo-redis-primary-key", redisPassword, withProvider);

        if (!config.appsEnabled()) {
            return new RuntimeResources(
                resourceGroupName,
                environment.name(),
                environment.id(),
                registry.name(),
                registry.loginServer(),
                keyVault.name(),
                keyVault.properties().applyValue(properties -> properties.vaultUri()),
                redis.name(),
                redis.hostName(),
                redis.sslPort(),
                Output.ofNullable(null),
                Output.ofNullable(null),
                Output.ofNullable(null),
                Output.ofNullable(null),
                Output.ofNullable(null)
            );
        }

        if (postgres == null) {
            throw new IllegalStateException("Runtime app deployment requires managed Postgres outputs.");
        }

        Output<String> acrUsername = ContainerregistryFunctions.listRegistryCredentials(ListRegistryCredentialsArgs.builder()
                .registryName(registry.name())
                .resourceGroupName(resourceGroupName)
                .build())
            .applyValue(credentials -> credentials.username().orElse(config.acrName()));
        Output<String> acrPassword = ContainerregistryFunctions.listRegistryCredentials(ListRegistryCredentialsArgs.builder()
                .registryName(registry.name())
                .resourceGroupName(resourceGroupName)
                .build())
            .applyValue(credentials -> credentials.passwords().getFirst().value().orElseThrow());

        Output<String> backendImage = registry.loginServer().applyValue(loginServer -> loginServer + "/mappo-backend:" + config.imageTag());
        Output<String> frontendImage = registry.loginServer().applyValue(loginServer -> loginServer + "/mappo-frontend:" + config.imageTag());
        Output<String> flywayImage = registry.loginServer().applyValue(loginServer -> loginServer + "/mappo-flyway:" + config.imageTag());

        List<SecretArgs> backendSecrets = new ArrayList<>();
        addSecret(backendSecrets, "database-url", postgres.databaseUrl());
        addSecret(backendSecrets, "database-password", postgres.password());
        addSecret(backendSecrets, "redis-password", redisPassword);
        addSecret(backendSecrets, "azure-client-id", config.azureClientId());
        addSecret(backendSecrets, "azure-client-secret", config.azureClientSecret());
        addSecret(backendSecrets, "marketplace-ingest-token", config.marketplaceIngestToken());
        addSecret(backendSecrets, "registry-password", acrPassword);
        addSecret(backendSecrets, "publisher-acr-pull-client-secret", config.publisherAcrPullClientSecret());
        addSecret(backendSecrets, "managed-app-release-webhook-secret", config.githubReleaseWebhookSecret());
        addSecret(backendSecrets, "managed-app-release-github-token", config.githubReleaseToken());
        addSecret(backendSecrets, "azure-devops-personal-access-token", config.azureDevOpsPat());
        addSecret(backendSecrets, "azure-devops-webhook-secret", config.azureDevOpsWebhookSecret());

        List<EnvironmentVarArgs> backendEnv = new ArrayList<>();
        backendEnv.add(secretEnv("MAPPO_DATABASE_URL", "database-url"));
        backendEnv.add(env("MAPPO_BACKEND_PORT", "8000"));
        backendEnv.add(env("MAPPO_DB_USER", postgres.adminLogin()));
        backendEnv.add(secretEnv("MAPPO_DB_PASSWORD", "database-password"));
        backendEnv.add(env("MAPPO_EXECUTION_MODE", "azure"));
        backendEnv.add(env("MAPPO_AZURE_TENANT_ID", config.tenantId()));
        backendEnv.add(secretEnv("MAPPO_AZURE_CLIENT_ID", "azure-client-id"));
        backendEnv.add(secretEnv("MAPPO_AZURE_CLIENT_SECRET", "azure-client-secret"));
        backendEnv.add(env("MAPPO_AZURE_TENANT_BY_SUBSCRIPTION", config.tenantBySubscription()));
        backendEnv.add(secretEnv("MAPPO_MARKETPLACE_INGEST_TOKEN", "marketplace-ingest-token"));
        backendEnv.add(env("MAPPO_RUN_RETENTION_DAYS", "90"));
        backendEnv.add(env("MAPPO_AUDIT_RETENTION_DAYS", "90"));
        backendEnv.add(env("MAPPO_REDIS_ENABLED", "true"));
        backendEnv.add(env("MAPPO_REDIS_HOST", redis.hostName()));
        backendEnv.add(env("MAPPO_REDIS_PORT", redis.sslPort().applyValue(String::valueOf)));
        backendEnv.add(env("MAPPO_REDIS_SSL_ENABLED", "true"));
        backendEnv.add(secretEnv("MAPPO_REDIS_PASSWORD", "redis-password"));
        backendEnv.add(env("MAPPO_AZURE_KEY_VAULT_URL", keyVault.properties().applyValue(properties -> properties.vaultUri())));
        backendEnv.add(env("MAPPO_CORS_ORIGINS", config.corsOrigins()));
        addOptionalSecretEnv(backendEnv, "MAPPO_PUBLISHER_ACR_PULL_CLIENT_SECRET", "publisher-acr-pull-client-secret", config.publisherAcrPullClientSecret());
        addOptionalSecretEnv(backendEnv, "MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET", "managed-app-release-webhook-secret", config.githubReleaseWebhookSecret());
        addOptionalSecretEnv(backendEnv, "MAPPO_MANAGED_APP_RELEASE_GITHUB_TOKEN", "managed-app-release-github-token", config.githubReleaseToken());
        addOptionalSecretEnv(backendEnv, "MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN", "azure-devops-personal-access-token", config.azureDevOpsPat());
        addOptionalSecretEnv(backendEnv, "MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET", "azure-devops-webhook-secret", config.azureDevOpsWebhookSecret());
        addOptionalValueEnv(backendEnv, "MAPPO_PUBLISHER_ACR_SERVER", config.publisherAcrServer());
        addOptionalValueEnv(backendEnv, "MAPPO_PUBLISHER_ACR_PULL_CLIENT_ID", config.publisherAcrPullClientId());
        addOptionalValueEnv(backendEnv, "MAPPO_PUBLISHER_ACR_PULL_SECRET_NAME", config.publisherAcrPullSecretName());

        ContainerApp backend = new ContainerApp(
            "runtime-backend-" + suffix,
            ContainerAppArgs.builder()
                .containerAppName(config.backendAppName())
                .resourceGroupName(resourceGroupName)
                .location(config.location())
                .managedEnvironmentId(environment.id())
                .identity(ManagedServiceIdentityArgs.builder()
                    .type(ManagedServiceIdentityType.UserAssigned)
                    .userAssignedIdentities(Output.all(managedIdentity.id()))
                    .build())
                .configuration(ConfigurationArgs.builder()
                    .activeRevisionsMode("Single")
                    .secrets(backendSecrets)
                    .registries(List.of(RegistryCredentialsArgs.builder()
                        .server(registry.loginServer())
                        .username(acrUsername)
                        .passwordSecretRef("registry-password")
                        .build()))
                    .ingress(externalIngress(8000))
                    .build())
                .template(TemplateArgs.builder()
                    .initContainers(List.of(InitContainerArgs.builder()
                        .name("flyway")
                        .image(flywayImage)
                        .env(List.of(
                            secretEnv("FLYWAY_URL", "database-url"),
                            env("FLYWAY_USER", postgres.adminLogin()),
                            secretEnv("FLYWAY_PASSWORD", "database-password"),
                            env("FLYWAY_CONNECT_RETRIES", "10")
                        ))
                        .resources(resources(config.migrationCpu(), config.migrationMemory()))
                        .build()))
                    .containers(List.of(ContainerArgs.builder()
                        .name("backend")
                        .image(backendImage)
                        .env(backendEnv)
                        .resources(resources(config.backendCpu(), config.backendMemory()))
                        .build()))
                    .scale(ScaleArgs.builder().minReplicas(config.minReplicas()).maxReplicas(config.maxReplicas()).build())
                    .build())
                .tags(PulumiSupport.linkedMapOfString("managedBy", "pulumi", "system", "mappo", "scope", "runtime-api"))
                .build(),
            withProvider
        );

        Output<String> backendUrl = backend.latestRevisionFqdn().applyValue(host -> "https://" + host);
        ApplicationRegistration easyAuthApp = null;
        ApplicationPassword easyAuthPassword = null;
        Output<String> easyAuthClientId = Output.ofNullable(null);
        if (config.easyAuthEnabled()) {
            easyAuthApp = new ApplicationRegistration(
                "runtime-easyauth-app-" + suffix,
                ApplicationRegistrationArgs.builder()
                    .displayName("mappo-ui-easyauth-" + suffix)
                    .signInAudience("AzureADMyOrg")
                    .build()
            );
            new ServicePrincipal(
                "runtime-easyauth-sp-" + suffix,
                ServicePrincipalArgs.builder()
                    .clientId(easyAuthApp.clientId())
                    .build()
            );
            easyAuthPassword = new ApplicationPassword(
                "runtime-easyauth-secret-" + suffix,
                ApplicationPasswordArgs.builder()
                    .applicationId(easyAuthApp.id())
                    .displayName("container-app-easyauth")
                    .endDateRelative("8760h")
                    .build()
            );
            easyAuthClientId = easyAuthApp.clientId();
        }

        List<SecretArgs> frontendSecrets = new ArrayList<>();
        addSecret(frontendSecrets, "registry-password", acrPassword);
        if (easyAuthPassword != null) {
            addSecret(frontendSecrets, "microsoft-provider-authentication-secret", easyAuthPassword.value());
        }

        ContainerApp frontend = new ContainerApp(
            "runtime-frontend-" + suffix,
            ContainerAppArgs.builder()
                .containerAppName(config.frontendAppName())
                .resourceGroupName(resourceGroupName)
                .location(config.location())
                .managedEnvironmentId(environment.id())
                .identity(ManagedServiceIdentityArgs.builder()
                    .type(ManagedServiceIdentityType.UserAssigned)
                    .userAssignedIdentities(Output.all(managedIdentity.id()))
                    .build())
                .configuration(ConfigurationArgs.builder()
                    .activeRevisionsMode("Single")
                    .secrets(frontendSecrets)
                    .registries(List.of(RegistryCredentialsArgs.builder()
                        .server(registry.loginServer())
                        .username(acrUsername)
                        .passwordSecretRef("registry-password")
                        .build()))
                    .ingress(externalIngress(80))
                    .build())
                .template(TemplateArgs.builder()
                    .containers(List.of(ContainerArgs.builder()
                        .name("frontend")
                        .image(frontendImage)
                        .env(List.of(env("MAPPO_API_BASE_URL", backendUrl)))
                        .resources(resources(config.frontendCpu(), config.frontendMemory()))
                        .build()))
                    .scale(ScaleArgs.builder().minReplicas(config.minReplicas()).maxReplicas(config.maxReplicas()).build())
                    .build())
                .tags(PulumiSupport.linkedMapOfString("managedBy", "pulumi", "system", "mappo", "scope", "runtime-ui"))
                .build(),
            withProvider
        );

        Output<String> frontendUrl = frontend.latestRevisionFqdn().applyValue(host -> "https://" + host);
        if (config.easyAuthEnabled()) {
            new ApplicationRedirectUris(
                "runtime-easyauth-redirects-" + suffix,
                ApplicationRedirectUrisArgs.builder()
                    .applicationId(easyAuthApp.id())
                    .type("Web")
                    .redirectUris(frontendUrl.applyValue(url -> List.of(url + "/.auth/login/aad/callback")))
                    .build()
            );
            new ContainerAppsAuthConfig(
                "runtime-frontend-auth-" + suffix,
                ContainerAppsAuthConfigArgs.builder()
                    .authConfigName("current")
                    .containerAppName(frontend.name())
                    .resourceGroupName(resourceGroupName)
                    .platform(AuthPlatformArgs.builder().enabled(true).build())
                    .globalValidation(GlobalValidationArgs.builder()
                        .unauthenticatedClientAction(UnauthenticatedClientActionV2.RedirectToLoginPage)
                        .redirectToProvider("AzureActiveDirectory")
                        .build())
                    .identityProviders(IdentityProvidersArgs.builder()
                        .azureActiveDirectory(AzureActiveDirectoryArgs.builder()
                            .enabled(true)
                            .registration(AzureActiveDirectoryRegistrationArgs.builder()
                                .clientId(easyAuthApp.clientId())
                                .clientSecretSettingName("microsoft-provider-authentication-secret")
                                .openIdIssuer("https://login.microsoftonline.com/" + config.tenantId() + "/v2.0")
                                .build())
                            .validation(AzureActiveDirectoryValidationArgs.builder()
                                .allowedAudiences(Output.all(easyAuthApp.clientId()))
                                .build())
                            .build())
                        .build())
                    .build(),
                withProvider
            );
        }

        return new RuntimeResources(
            resourceGroupName,
            environment.name(),
            environment.id(),
            registry.name(),
            registry.loginServer(),
            keyVault.name(),
            keyVault.properties().applyValue(properties -> properties.vaultUri()),
            redis.name(),
            redis.hostName(),
            redis.sslPort(),
            backend.name(),
            backendUrl,
            frontend.name(),
            frontendUrl,
            easyAuthClientId
        );
    }

    private static List<AccessPolicyEntryArgs> keyVaultAccessPolicies(RuntimeConfig config, UserAssignedIdentity managedIdentity) {
        List<AccessPolicyEntryArgs> policies = new ArrayList<>();
        PermissionsArgs secretPermissions = PermissionsArgs.builder()
            .secrets(
                Either.ofRight(SecretPermissions.Get),
                Either.ofRight(SecretPermissions.List),
                Either.ofRight(SecretPermissions.Set),
                Either.ofRight(SecretPermissions.Delete)
            )
            .build();
        policies.add(AccessPolicyEntryArgs.builder()
            .tenantId(config.tenantId())
            .objectId(managedIdentity.principalId())
            .permissions(secretPermissions)
            .build());
        if (!config.keyVaultAccessObjectId().isBlank()) {
            policies.add(AccessPolicyEntryArgs.builder()
                .tenantId(config.tenantId())
                .objectId(config.keyVaultAccessObjectId())
                .permissions(secretPermissions)
                .build());
        }
        return policies;
    }

    private static IngressArgs externalIngress(int targetPort) {
        return IngressArgs.builder()
            .external(true)
            .targetPort(targetPort)
            .traffic(List.of(TrafficWeightArgs.builder().latestRevision(true).weight(100).build()))
            .build();
    }

    private static ContainerResourcesArgs resources(double cpu, String memory) {
        return ContainerResourcesArgs.builder().cpu(cpu).memory(memory).build();
    }

    private static EnvironmentVarArgs env(String name, String value) {
        return EnvironmentVarArgs.builder().name(name).value(value).build();
    }

    private static EnvironmentVarArgs env(String name, Output<String> value) {
        return EnvironmentVarArgs.builder().name(name).value(value).build();
    }

    private static EnvironmentVarArgs secretEnv(String name, String secretName) {
        return EnvironmentVarArgs.builder().name(name).secretRef(secretName).build();
    }

    private static void addSecret(List<SecretArgs> secrets, String name, Output<String> value) {
        if (value != null) {
            secrets.add(SecretArgs.builder().name(name).value(value).build());
        }
    }

    private static void addOptionalSecretEnv(List<EnvironmentVarArgs> env, String name, String secretName, Output<String> value) {
        if (value != null) {
            env.add(secretEnv(name, secretName));
        }
    }

    private static void addOptionalValueEnv(List<EnvironmentVarArgs> env, String name, Output<String> value) {
        if (value != null) {
            env.add(env(name, value));
        }
    }

    private static void writeKeyVaultSecret(
        String resourceName,
        Vault keyVault,
        Output<String> resourceGroupName,
        String secretName,
        Output<String> value,
        CustomResourceOptions options
    ) {
        if (value == null) {
            return;
        }
        new Secret(
            resourceName,
            com.pulumi.azurenative.keyvault.SecretArgs.builder()
                .secretName(secretName)
                .resourceGroupName(resourceGroupName)
                .vaultName(keyVault.name())
                .properties(SecretPropertiesArgs.builder()
                    .value(value)
                    .contentType("mappo/runtime")
                    .build())
                .tags(PulumiSupport.linkedMapOfString("managedBy", "pulumi", "system", "mappo"))
                .build(),
            options
        );
    }
}
