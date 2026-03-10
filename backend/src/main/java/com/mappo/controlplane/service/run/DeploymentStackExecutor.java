package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.execution.DeploymentDriver;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface DeploymentStackExecutor extends DeploymentDriver {

    @Override
    default boolean supports(ReleaseRecord release, boolean azureConfigured) {
        return azureConfigured
            && release.sourceType() == MappoReleaseSourceType.deployment_stack
            && release.deploymentScope() == MappoDeploymentScope.resource_group;
    }

    @Override
    TargetDeploymentOutcome deploy(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    );
}
