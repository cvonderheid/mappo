package com.mappo.pulumi;

import com.pulumi.core.Output;

record PlatformResources(
    Output<String> resourceGroupName,
    Output<String> containerEnvironmentName,
    Output<String> containerEnvironmentId,
    Output<String> containerEnvironmentDefaultDomain,
    Output<String> acrName,
    Output<String> acrLoginServer,
    Output<String> keyVaultName,
    Output<String> keyVaultUri,
    Output<String> redisName,
    Output<String> redisHost,
    Output<Integer> redisPort,
    Output<String> redisPassword,
    Output<String> managedIdentityId,
    Output<String> managedIdentityClientId,
    Output<String> managedIdentityPrincipalId,
    Output<String> controlPlanePostgresResourceGroupName,
    Output<String> controlPlanePostgresServerName,
    Output<String> controlPlanePostgresHost,
    Output<Integer> controlPlanePostgresPort,
    Output<String> controlPlanePostgresDatabase,
    Output<String> controlPlanePostgresAdmin,
    Output<String> controlPlanePostgresConnectionUsername,
    Output<String> controlPlanePostgresPassword,
    Output<String> controlPlaneDatabaseUrl
) {
}
