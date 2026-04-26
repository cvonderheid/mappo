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
        ControlPlanePostgresResources controlPlanePostgres = createControlPlanePostgresResources();
        RuntimeResources runtime = createRuntimeResources(controlPlanePostgres);

        InfrastructureExports.exportControlPlanePostgres(ctx, config.controlPlanePostgres(), controlPlanePostgres);
        InfrastructureExports.exportRuntime(ctx, config.runtime(), runtime);
    }

    private ControlPlanePostgresResources createControlPlanePostgresResources() {
        return ControlPlanePostgresStack.create(
            config.controlPlanePostgres(),
            providers.get(config.controlPlanePostgres().subscriptionId())
        );
    }

    private RuntimeResources createRuntimeResources(ControlPlanePostgresResources controlPlanePostgres) {
        return RuntimeStack.create(
            config.runtime(),
            controlPlanePostgres,
            providers.get(config.runtime().subscriptionId())
        );
    }
}
