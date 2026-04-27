package com.mappo.pulumi;

import com.pulumi.Context;
import com.pulumi.core.Output;

final class InfrastructureExports {
    private InfrastructureExports() {
    }

    static void exportPlatform(Context ctx, InfrastructureConfig config, PlatformResources platform) {
        ctx.export("stackKind", config.stackKind());
        ctx.export("runtimeSubscriptionId", Output.of(config.runtime().subscriptionId()));
        ctx.export("runtimeResourceGroupName", platform.resourceGroupName());
        ctx.export("runtimeContainerEnvironmentName", platform.containerEnvironmentName());
        ctx.export("runtimeContainerEnvironmentId", platform.containerEnvironmentId());
        ctx.export("runtimeContainerEnvironmentDefaultDomain", platform.containerEnvironmentDefaultDomain());
        ctx.export("runtimeAcrName", platform.acrName());
        ctx.export("runtimeAcrLoginServer", platform.acrLoginServer());
        ctx.export("runtimeKeyVaultName", platform.keyVaultName());
        ctx.export("runtimeKeyVaultUri", platform.keyVaultUri());
        ctx.export("runtimeRedisName", platform.redisName());
        ctx.export("runtimeRedisHost", platform.redisHost());
        ctx.export("runtimeRedisPort", platform.redisPort());
        ctx.export("runtimeRedisPassword", platform.redisPassword());
        ctx.export("runtimeManagedIdentityId", platform.managedIdentityId());
        ctx.export("runtimeManagedIdentityClientId", platform.managedIdentityClientId());
        ctx.export("runtimeManagedIdentityPrincipalId", platform.managedIdentityPrincipalId());
        ctx.export("controlPlanePostgresEnabled", config.controlPlanePostgres().enabled());
        ctx.export("controlPlanePostgresResourceGroupName", platform.controlPlanePostgresResourceGroupName());
        ctx.export("controlPlanePostgresServerName", platform.controlPlanePostgresServerName());
        ctx.export("controlPlanePostgresHost", platform.controlPlanePostgresHost());
        ctx.export("controlPlanePostgresPort", platform.controlPlanePostgresPort());
        ctx.export("controlPlanePostgresDatabase", platform.controlPlanePostgresDatabase());
        ctx.export("controlPlanePostgresAdmin", platform.controlPlanePostgresAdmin());
        ctx.export("controlPlanePostgresConnectionUsername", platform.controlPlanePostgresConnectionUsername());
        ctx.export("controlPlanePostgresPassword", platform.controlPlanePostgresPassword());
        ctx.export("controlPlaneDatabaseUrl", platform.controlPlaneDatabaseUrl());
    }

    static void exportRuntimeApps(
        Context ctx,
        InfrastructureConfig config,
        PlatformResources platform,
        RuntimeAppResources apps
    ) {
        ctx.export("stackKind", config.stackKind());
        ctx.export("platformStack", Output.of(config.platformStack()));
        ctx.export("runtimeSubscriptionId", Output.of(config.runtime().subscriptionId()));
        ctx.export("runtimeResourceGroupName", platform.resourceGroupName());
        ctx.export("runtimeContainerEnvironmentName", platform.containerEnvironmentName());
        ctx.export("runtimeContainerEnvironmentId", platform.containerEnvironmentId());
        ctx.export("runtimeContainerEnvironmentDefaultDomain", platform.containerEnvironmentDefaultDomain());
        ctx.export("runtimeAcrName", platform.acrName());
        ctx.export("runtimeAcrLoginServer", platform.acrLoginServer());
        ctx.export("runtimeKeyVaultName", platform.keyVaultName());
        ctx.export("runtimeKeyVaultUri", platform.keyVaultUri());
        ctx.export("runtimeRedisName", platform.redisName());
        ctx.export("runtimeRedisHost", platform.redisHost());
        ctx.export("runtimeRedisPort", platform.redisPort());
        ctx.export("runtimeBackendAppName", apps.backendAppName());
        ctx.export("runtimeBackendUrl", apps.backendUrl());
        ctx.export("runtimeFrontendAppName", apps.frontendAppName());
        ctx.export("runtimeFrontendUrl", apps.frontendUrl());
        ctx.export("runtimeEasyAuthApplicationClientId", apps.easyAuthApplicationClientId());
        ctx.export("runtimeImageTag", Output.of(config.runtime().imageTag()));
    }
}
