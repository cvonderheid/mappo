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
        FrontDoorResources frontDoorResources = createFrontDoorResources();
        ControlPlanePostgresResources controlPlanePostgres = createControlPlanePostgresResources();

        InfrastructureExports.exportControlPlanePostgres(ctx, config.controlPlanePostgres(), controlPlanePostgres);
        InfrastructureExports.exportFrontDoor(ctx, config.frontDoor(), frontDoorResources);
    }

    private FrontDoorResources createFrontDoorResources() {
        if (!config.frontDoor().enabled()) {
            return null;
        }
        return FrontDoorResources.create(
            ctx,
            config.frontDoor(),
            providers.get(config.frontDoor().subscriptionId()),
            config.frontDoor().hasAzureDnsZone() ? providers.get(config.frontDoor().dnsZoneSubscriptionId()) : null
        );
    }

    private ControlPlanePostgresResources createControlPlanePostgresResources() {
        return ControlPlanePostgresStack.create(
            config.controlPlanePostgres(),
            providers.get(config.controlPlanePostgres().subscriptionId())
        );
    }
}
