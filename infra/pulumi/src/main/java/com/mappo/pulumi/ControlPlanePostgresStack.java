package com.mappo.pulumi;

import com.pulumi.azurenative.Provider;
import com.pulumi.azurenative.dbforpostgresql.Configuration;
import com.pulumi.azurenative.dbforpostgresql.ConfigurationArgs;
import com.pulumi.azurenative.dbforpostgresql.Database;
import com.pulumi.azurenative.dbforpostgresql.DatabaseArgs;
import com.pulumi.azurenative.dbforpostgresql.FirewallRule;
import com.pulumi.azurenative.dbforpostgresql.FirewallRuleArgs;
import com.pulumi.azurenative.dbforpostgresql.Server;
import com.pulumi.azurenative.dbforpostgresql.ServerArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.BackupArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.HighAvailabilityArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.NetworkArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.SkuArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.StorageArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.pulumi.core.Output;
import com.pulumi.random.RandomPassword;
import com.pulumi.random.RandomPasswordArgs;
import com.pulumi.resources.CustomResourceOptions;
import com.pulumi.resources.CustomTimeouts;
import java.time.Duration;

final class ControlPlanePostgresStack {
    private ControlPlanePostgresStack() {
    }

    static ControlPlanePostgresResources create(ControlPlanePostgresConfig config, Provider provider) {
        if (!config.enabled()) {
            return null;
        }
        String resourceNameSuffix = config.resourceNameSuffix();
        String resourceGroupName = PulumiSupport.normalizeName(
            config.resourceGroupName(),
            "rg-mappo-control-plane-" + resourceNameSuffix,
            90
        );
        String serverName = PulumiSupport.normalizeName(
            config.serverNamePrefix() + "-" + resourceNameSuffix,
            "pg-mappo-" + resourceNameSuffix,
            63
        );

        CustomResourceOptions withProvider = CustomResourceOptions.builder().provider(provider).build();
        Output<String> adminPassword = config.adminPassword();
        if (adminPassword == null) {
            RandomPassword generatedPassword = new RandomPassword(
                "control-plane-postgres-password-" + resourceNameSuffix,
                RandomPasswordArgs.builder()
                    .length(32)
                    .special(true)
                    .overrideSpecial("_%@")
                    .build()
            );
            adminPassword = generatedPassword.result();
        }

        ResourceGroup resourceGroup = new ResourceGroup(
            "control-plane-rg-" + resourceNameSuffix,
            ResourceGroupArgs.builder()
                .resourceGroupName(resourceGroupName)
                .location(config.location())
                .tags(PulumiSupport.linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "control-plane"
                ))
                .build(),
            withProvider
        );

        Server server = new Server(
            "control-plane-postgres-" + resourceNameSuffix,
            ServerArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .serverName(serverName)
                .location(config.location())
                .createMode("Create")
                .administratorLogin(config.adminLogin())
                .administratorLoginPassword(adminPassword)
                .version(config.version())
                .backup(BackupArgs.builder()
                    .backupRetentionDays(config.backupRetentionDays())
                    .geoRedundantBackup("Disabled")
                    .build())
                .highAvailability(HighAvailabilityArgs.builder().mode("Disabled").build())
                .network(NetworkArgs.builder()
                    .publicNetworkAccess(config.publicNetworkAccess() ? "Enabled" : "Disabled")
                    .build())
                .sku(SkuArgs.builder()
                    .name(config.skuName())
                    .tier(PulumiSupport.inferPostgresSkuTier(config.skuName()))
                    .build())
                .storage(StorageArgs.builder()
                    .storageSizeGB(config.storageSizeGb())
                    .autoGrow("Enabled")
                    .build())
                .tags(PulumiSupport.linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "control-plane-postgres"
                ))
                .build(),
            CustomResourceOptions.builder()
                .provider(provider)
                .customTimeouts(CustomTimeouts.builder()
                    .create(Duration.ofMinutes(30))
                    .update(Duration.ofMinutes(30))
                    .delete(Duration.ofMinutes(30))
                    .build())
                .build()
        );

        Database database = new Database(
            "control-plane-postgres-db-" + resourceNameSuffix,
            DatabaseArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .serverName(server.name())
                .databaseName(config.databaseName())
                .charset("UTF8")
                .collation("en_US.utf8")
                .build(),
            withProvider
        );

        new Configuration(
            "control-plane-postgres-ext-" + resourceNameSuffix,
            ConfigurationArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .serverName(server.name())
                .configurationName("azure.extensions")
                .source("user-override")
                .value("PGCRYPTO")
                .build(),
            CustomResourceOptions.builder()
                .provider(provider)
                .dependsOn(database)
                .customTimeouts(CustomTimeouts.builder()
                    .create(Duration.ofMinutes(10))
                    .update(Duration.ofMinutes(10))
                    .build())
                .build()
        );

        if (config.publicNetworkAccess()) {
            if (config.allowAzureServices()) {
                new FirewallRule(
                    "control-plane-postgres-fw-azure-" + resourceNameSuffix,
                    FirewallRuleArgs.builder()
                        .resourceGroupName(resourceGroup.name())
                        .serverName(server.name())
                        .firewallRuleName("allow-azure-services")
                        .startIpAddress("0.0.0.0")
                        .endIpAddress("0.0.0.0")
                        .build(),
                    withProvider
                );
            }

            for (int i = 0; i < config.firewallIpRanges().size(); i++) {
                PulumiSupport.FirewallIpRange range = config.firewallIpRanges().get(i);
                new FirewallRule(
                    "control-plane-postgres-fw-custom-" + resourceNameSuffix + "-" + (i + 1),
                    FirewallRuleArgs.builder()
                        .resourceGroupName(resourceGroup.name())
                        .serverName(server.name())
                        .firewallRuleName("allow-ip-" + (i + 1))
                        .startIpAddress(range.startIpAddress())
                        .endIpAddress(range.endIpAddress())
                        .build(),
                    withProvider
                );
            }
        }

        Output<String> host = server.fullyQualifiedDomainName();
        Output<String> connectionUsername = Output.of(config.adminLogin());
        Output<String> databaseUrl = Output.tuple(host, database.name())
            .applyValue(tuple -> "jdbc:postgresql://"
                + tuple.t1
                + ":5432/"
                + tuple.t2
                + "?sslmode=require");

        return new ControlPlanePostgresResources(
            resourceGroup.name(),
            server.name(),
            host,
            5432,
            config.databaseName(),
            config.adminLogin(),
            connectionUsername,
            adminPassword,
            databaseUrl
        );
    }
}
