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

    static void exportFrontDoor(Context ctx, FrontDoorConfig config, FrontDoorResources frontDoorResources) {
        ctx.export("frontDoorEnabled", frontDoorResources != null);
        ctx.export(
            "frontDoorSubscriptionId",
            frontDoorResources == null ? Output.ofNullable(null) : Output.of(config.subscriptionId())
        );
        ctx.export(
            "frontDoorResourceGroupName",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.resourceGroup().name()
        );
        ctx.export(
            "frontDoorProfileName",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.profile().name()
        );
        ctx.export(
            "frontDoorEndpointName",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.endpoint().name()
        );
        ctx.export(
            "frontDoorDefaultHostname",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.defaultHostname()
        );
        ctx.export(
            "frontDoorDefaultWebhookUrl",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.defaultWebhookUrl()
        );
        ctx.export(
            "frontDoorOriginHost",
            frontDoorResources == null ? Output.ofNullable(null) : Output.of(config.originHost())
        );
        ctx.export(
            "frontDoorCustomDomainHostName",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.customDomainHostname()
        );
        ctx.export(
            "frontDoorCustomDomainValidationState",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.customDomainValidationState()
        );
        ctx.export(
            "frontDoorCustomDomainValidationToken",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.customDomainValidationToken()
        );
        ctx.export(
            "frontDoorDnsTxtRecordName",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.dnsTxtRecordName()
        );
        ctx.export(
            "frontDoorDnsTxtRecordValue",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.dnsTxtRecordValue()
        );
        ctx.export(
            "frontDoorDnsCnameRecordName",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.dnsCnameRecordName()
        );
        ctx.export(
            "frontDoorDnsCnameRecordValue",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.dnsCnameRecordValue()
        );
        ctx.export(
            "managedAppReleaseWebhookUrl",
            frontDoorResources == null ? Output.ofNullable(null) : frontDoorResources.finalWebhookUrl()
        );
    }
}
