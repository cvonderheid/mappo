package com.mappo.pulumi;

import com.pulumi.core.Output;

record RuntimeResources(
    Output<String> resourceGroupName,
    Output<String> containerEnvironmentName,
    Output<String> containerEnvironmentId,
    Output<String> acrName,
    Output<String> acrLoginServer,
    Output<String> keyVaultName,
    Output<String> keyVaultUri,
    Output<String> redisName,
    Output<String> redisHost,
    Output<Integer> redisPort,
    Output<String> backendAppName,
    Output<String> backendUrl,
    Output<String> frontendAppName,
    Output<String> frontendUrl,
    Output<String> easyAuthApplicationClientId
) {
}
