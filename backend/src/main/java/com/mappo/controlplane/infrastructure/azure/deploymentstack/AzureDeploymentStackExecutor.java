package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.core.management.exception.ManagementException;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.run.DeploymentStackExecutor;
import com.mappo.controlplane.service.run.TargetDeploymentOutcome;
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
        TargetExecutionContextRecord target
    ) {
        AzureDeploymentStackOperationContext context = null;

        try {
            context = contextFactory.resolve(project, release, target);
            return applyService.apply(context, runId, target.targetId());
        } catch (ManagementException error) {
            return exceptionTranslator.handleManagementException(context, runId, target.targetId(), error);
        } catch (IllegalArgumentException error) {
            throw exceptionTranslator.configurationFailure(context, runId, target.targetId(), error);
        }
    }
}
