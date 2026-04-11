package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.core.management.exception.ManagementException;
import com.mappo.controlplane.integrations.azure.access.AzureWorkloadRbacTargetAccessContext;
import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.integrations.azure.deploymentstack.DeploymentStackExecutor;
import com.mappo.controlplane.domain.execution.TargetDeploymentOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackExecutor implements DeploymentStackExecutor {

    private final AzureDeploymentStackOperationContextFactory contextFactory;
    private final AzureDeploymentStackApplyService applyService;
    private final AzureDeploymentStackExceptionTranslator exceptionTranslator;

    @Override
    public TargetDeploymentOutcome deploy(
        String runId,
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        ResolvedTargetAccessContext accessContext
    ) {
        AzureDeploymentStackOperationContext context = null;
        AzureWorkloadRbacTargetAccessContext azureAccessContext = requireAzureAccessContext(accessContext);

        try {
            context = contextFactory.resolve(project, release, target, azureAccessContext);
            return applyService.apply(context, runId, target.targetId());
        } catch (ManagementException error) {
            return exceptionTranslator.handleManagementException(context, runId, target.targetId(), error);
        } catch (IllegalArgumentException error) {
            throw exceptionTranslator.configurationFailure(context, runId, target.targetId(), error);
        }
    }

    private AzureWorkloadRbacTargetAccessContext requireAzureAccessContext(ResolvedTargetAccessContext accessContext) {
        if (accessContext instanceof AzureWorkloadRbacTargetAccessContext azureAccessContext) {
            return azureAccessContext;
        }
        throw new IllegalArgumentException("deployment_stack execution requires an Azure workload RBAC access context");
    }
}
