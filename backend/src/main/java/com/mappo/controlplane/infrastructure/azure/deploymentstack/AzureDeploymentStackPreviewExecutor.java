package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.mappo.controlplane.domain.access.AzureWorkloadRbacTargetAccessContext;
import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
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
    public TargetPreviewOutcome preview(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        ResolvedTargetAccessContext accessContext
    ) {
        AzureDeploymentStackOperationContext context = contextFactory.resolve(
            project,
            release,
            target,
            requireAzureAccessContext(accessContext)
        );
        return previewOperationService.preview(context, target.targetId());
    }

    private AzureWorkloadRbacTargetAccessContext requireAzureAccessContext(ResolvedTargetAccessContext accessContext) {
        if (accessContext instanceof AzureWorkloadRbacTargetAccessContext azureAccessContext) {
            return azureAccessContext;
        }
        throw new IllegalArgumentException("deployment_stack preview requires an Azure workload RBAC access context");
    }
}
