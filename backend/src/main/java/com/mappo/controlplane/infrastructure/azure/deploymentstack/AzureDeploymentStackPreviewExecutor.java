package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.DeploymentStackPreviewExecutor;
import com.mappo.controlplane.service.run.TargetPreviewOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackPreviewExecutor implements DeploymentStackPreviewExecutor {

    private final AzureDeploymentStackOperationContextFactory contextFactory;
    private final AzureDeploymentStackPreviewOperationService previewOperationService;

    @Override
    public TargetPreviewOutcome preview(ProjectDefinition project, ReleaseRecord release, TargetExecutionContextRecord target) {
        AzureDeploymentStackOperationContext context = contextFactory.resolve(project, release, target);
        return previewOperationService.preview(context, target.targetId());
    }
}
