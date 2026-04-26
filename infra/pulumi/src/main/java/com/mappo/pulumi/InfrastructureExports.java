package com.mappo.pulumi;

import com.pulumi.Context;
import com.pulumi.core.Output;

final class InfrastructureExports {
    private InfrastructureExports() {
    }

    static void exportControlPlanePostgres(
        Context ctx,
        ControlPlanePostgresConfig config,
        ControlPlanePostgresResources postgres
    ) {
        ctx.export("controlPlanePostgresEnabled", config.enabled());
        ctx.export(
            "controlPlanePostgresSubscriptionId",
            postgres == null ? Output.ofNullable(null) : Output.of(postgres.subscriptionId())
        );
        ctx.export(
            "controlPlanePostgresResourceGroupName",
            postgres == null ? Output.ofNullable(null) : postgres.resourceGroupName()
        );
        ctx.export(
            "controlPlanePostgresServerName",
            postgres == null ? Output.ofNullable(null) : postgres.serverName()
        );
        ctx.export(
            "controlPlanePostgresHost",
            postgres == null ? Output.ofNullable(null) : postgres.host()
        );
        ctx.export(
            "controlPlanePostgresPort",
            postgres == null ? Output.ofNullable(null) : Output.of(postgres.port())
        );
        ctx.export(
            "controlPlanePostgresDatabase",
            postgres == null ? Output.ofNullable(null) : Output.of(postgres.databaseName())
        );
        ctx.export(
            "controlPlanePostgresAdmin",
            postgres == null ? Output.ofNullable(null) : Output.of(postgres.adminLogin())
        );
        ctx.export(
            "controlPlanePostgresConnectionUsername",
            postgres == null ? Output.ofNullable(null) : postgres.connectionUsername()
        );
        ctx.export(
            "controlPlanePostgresPassword",
            postgres == null ? Output.ofNullable(null) : postgres.password()
        );
        ctx.export(
            "controlPlaneDatabaseUrl",
            postgres == null ? Output.ofNullable(null) : postgres.databaseUrl()
        );
    }

    static void exportRuntime(Context ctx, RuntimeConfig config, RuntimeResources runtime) {
        ctx.export("runtimeEnabled", config.enabled());
        ctx.export("runtimeAppsEnabled", config.appsEnabled());
        ctx.export("runtimeSubscriptionId", Output.of(config.subscriptionId()));
        ctx.export("runtimeResourceGroupName", runtime == null ? Output.ofNullable(null) : runtime.resourceGroupName());
        ctx.export("runtimeContainerEnvironmentName", runtime == null ? Output.ofNullable(null) : runtime.containerEnvironmentName());
        ctx.export("runtimeContainerEnvironmentId", runtime == null ? Output.ofNullable(null) : runtime.containerEnvironmentId());
        ctx.export("runtimeAcrName", runtime == null ? Output.ofNullable(null) : runtime.acrName());
        ctx.export("runtimeAcrLoginServer", runtime == null ? Output.ofNullable(null) : runtime.acrLoginServer());
        ctx.export("runtimeKeyVaultName", runtime == null ? Output.ofNullable(null) : runtime.keyVaultName());
        ctx.export("runtimeKeyVaultUri", runtime == null ? Output.ofNullable(null) : runtime.keyVaultUri());
        ctx.export("runtimeRedisName", runtime == null ? Output.ofNullable(null) : runtime.redisName());
        ctx.export("runtimeRedisHost", runtime == null ? Output.ofNullable(null) : runtime.redisHost());
        ctx.export("runtimeRedisPort", runtime == null ? Output.ofNullable(null) : runtime.redisPort());
        ctx.export("runtimeBackendAppName", runtime == null ? Output.ofNullable(null) : runtime.backendAppName());
        ctx.export("runtimeBackendUrl", runtime == null ? Output.ofNullable(null) : runtime.backendUrl());
        ctx.export("runtimeFrontendAppName", runtime == null ? Output.ofNullable(null) : runtime.frontendAppName());
        ctx.export("runtimeFrontendUrl", runtime == null ? Output.ofNullable(null) : runtime.frontendUrl());
        ctx.export("runtimeEasyAuthApplicationClientId", runtime == null ? Output.ofNullable(null) : runtime.easyAuthApplicationClientId());
        ctx.export("runtimeImageTag", Output.of(config.imageTag()));
    }
}
