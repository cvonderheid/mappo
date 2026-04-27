package com.mappo.pulumi;

import com.pulumi.core.Output;

record RuntimeAppResources(
    Output<String> backendAppName,
    Output<String> backendUrl,
    Output<String> frontendAppName,
    Output<String> frontendUrl,
    Output<String> easyAuthApplicationClientId
) {
}
