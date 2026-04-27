package com.mappo.pulumi;

import com.pulumi.core.Output;

record ControlPlanePostgresResources(
    Output<String> resourceGroupName,
    Output<String> serverName,
    Output<String> host,
    int port,
    String databaseName,
    String adminLogin,
    Output<String> connectionUsername,
    Output<String> password,
    Output<String> databaseUrl
) {
}
