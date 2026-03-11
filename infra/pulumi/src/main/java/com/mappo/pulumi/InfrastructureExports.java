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
}
