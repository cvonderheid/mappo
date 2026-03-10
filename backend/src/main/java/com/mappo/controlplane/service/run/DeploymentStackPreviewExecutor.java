package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.execution.DeploymentPreviewDriver;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunPreviewMode;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface DeploymentStackPreviewExecutor extends DeploymentPreviewDriver {

    @Override
    default boolean supports(ReleaseRecord release, boolean azureConfigured) {
        return azureConfigured
            && release.sourceType() == MappoReleaseSourceType.deployment_stack
            && release.deploymentScope() == MappoDeploymentScope.resource_group;
    }

    @Override
    default RunPreviewMode mode() {
        return RunPreviewMode.ARM_WHAT_IF;
    }

    @Override
    TargetPreviewOutcome preview(ReleaseRecord release, TargetExecutionContextRecord target);
}
