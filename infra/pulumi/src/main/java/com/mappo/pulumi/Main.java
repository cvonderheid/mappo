package com.mappo.pulumi;

import com.pulumi.Config;
import com.pulumi.Context;
import com.pulumi.Pulumi;

public final class Main {
    private final Context ctx;
    private final InfrastructureConfig config;
    private final AzureProviderRegistry providers;

    private Main(Context ctx) {
        this.ctx = ctx;
        this.config = InfrastructureConfig.load(Config.of("mappo"), ctx.stackName());
        this.providers = new AzureProviderRegistry();
    }

    public static void main(String[] args) {
        Pulumi.run(ctx -> new Main(ctx).run());
    }

    private void run() {
        if ("platform".equals(config.stackKind())) {
            runPlatform();
            return;
        }
        runRuntime();
    }

    private void runPlatform() {
        ControlPlanePostgresResources controlPlanePostgres = ControlPlanePostgresStack.create(
            config.controlPlanePostgres(),
            providers.get(config.runtime().subscriptionId())
        );
        PlatformResources platform = RuntimeStack.createPlatform(
            config.runtime(),
            controlPlanePostgres,
            providers.get(config.runtime().subscriptionId())
        );
        InfrastructureExports.exportPlatform(ctx, config, platform);
    }

    private void runRuntime() {
        PlatformResources platform = PlatformStackReference.load(config.platformStack());
        RuntimeAppResources apps = RuntimeStack.createApps(
            config.runtime(),
            platform,
            providers.get(config.runtime().subscriptionId())
        );
        InfrastructureExports.exportRuntimeApps(ctx, config, platform, apps);
    }
}
