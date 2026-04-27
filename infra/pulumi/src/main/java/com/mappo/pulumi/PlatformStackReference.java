package com.mappo.pulumi;

import com.pulumi.core.Output;
import com.pulumi.resources.StackReference;
import com.pulumi.resources.StackReferenceArgs;

final class PlatformStackReference {
    private PlatformStackReference() {
    }

    static PlatformResources load(String stackName) {
        StackReference reference = new StackReference(
            "platform-stack",
            StackReferenceArgs.builder().name(stackName).build()
        );
        return new PlatformResources(
            stringOutput(reference, "runtimeResourceGroupName"),
            stringOutput(reference, "runtimeContainerEnvironmentName"),
            stringOutput(reference, "runtimeContainerEnvironmentId"),
            stringOutput(reference, "runtimeContainerEnvironmentDefaultDomain"),
            stringOutput(reference, "runtimeAcrName"),
            stringOutput(reference, "runtimeAcrLoginServer"),
            stringOutput(reference, "runtimeKeyVaultName"),
            stringOutput(reference, "runtimeKeyVaultUri"),
            stringOutput(reference, "runtimeRedisName"),
            stringOutput(reference, "runtimeRedisHost"),
            integerOutput(reference, "runtimeRedisPort"),
            stringOutput(reference, "runtimeRedisPassword"),
            stringOutput(reference, "runtimeManagedIdentityId"),
            stringOutput(reference, "runtimeManagedIdentityClientId"),
            stringOutput(reference, "runtimeManagedIdentityPrincipalId"),
            stringOutput(reference, "controlPlanePostgresResourceGroupName"),
            stringOutput(reference, "controlPlanePostgresServerName"),
            stringOutput(reference, "controlPlanePostgresHost"),
            integerOutput(reference, "controlPlanePostgresPort"),
            stringOutput(reference, "controlPlanePostgresDatabase"),
            stringOutput(reference, "controlPlanePostgresAdmin"),
            stringOutput(reference, "controlPlanePostgresConnectionUsername"),
            stringOutput(reference, "controlPlanePostgresPassword"),
            stringOutput(reference, "controlPlaneDatabaseUrl")
        );
    }

    private static Output<String> stringOutput(StackReference reference, String name) {
        return reference.requireOutput(name).applyValue(value -> value == null ? null : String.valueOf(value));
    }

    private static Output<Integer> integerOutput(StackReference reference, String name) {
        return reference.requireOutput(name).applyValue(value -> {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        });
    }
}
