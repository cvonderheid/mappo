package com.mappo.pulumi;

import com.pulumi.core.Output;
import java.util.List;

record ControlPlanePostgresConfig(
    boolean enabled,
    String subscriptionId,
    String location,
    String resourceGroupPrefix,
    String serverNamePrefix,
    String databaseName,
    String adminLogin,
    String version,
    String skuName,
    int storageSizeGb,
    int backupRetentionDays,
    boolean publicNetworkAccess,
    boolean allowAzureServices,
    Output<String> adminPassword,
    List<PulumiSupport.FirewallIpRange> firewallIpRanges
) {
}
