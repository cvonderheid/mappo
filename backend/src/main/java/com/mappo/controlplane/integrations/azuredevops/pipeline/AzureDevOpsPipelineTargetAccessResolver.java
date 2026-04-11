package com.mappo.controlplane.integrations.azuredevops.pipeline;

import com.mappo.controlplane.integrations.azure.access.AzureWorkloadRbacTargetAccessContext;
import com.mappo.controlplane.domain.access.TargetAccessResolver;
import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.integrations.azure.access.config.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.run.DefaultTargetAccessResolver;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
class AzureDevOpsPipelineTargetAccessResolver implements TargetAccessResolver {

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return project.accessStrategy() == ProjectAccessStrategyType.azure_workload_rbac
            && project.deploymentDriver() == ProjectDeploymentDriverType.pipeline_trigger
            && project.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig config
            && "azure_devops".equalsIgnoreCase(normalize(config.pipelineSystem()));
    }

    @Override
    public TargetAccessValidation validate(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean runtimeConfigured
    ) {
        if (context == null) {
            return TargetAccessValidation.failure(
                "Target is missing registration metadata required for Azure DevOps execution.",
                DefaultTargetAccessResolver.invalidTargetConfiguration(
                    "Target is missing registration metadata required for Azure DevOps execution.",
                    null,
                    null
                )
            );
        }

        if (context.subscriptionId() == null || context.tenantId() == null) {
            return TargetAccessValidation.failure(
                "Target tenant/subscription metadata is missing for Azure DevOps execution.",
                DefaultTargetAccessResolver.invalidTargetConfiguration(
                    "Target tenant/subscription metadata is missing for Azure DevOps execution.",
                    "tenantId or subscriptionId is null",
                    context.containerAppResourceId()
                )
            );
        }

        Map<String, String> config = context.executionConfig();
        String resourceGroup = firstNonBlank(value(config, "targetResourceGroup"), value(config, "resourceGroup"));
        String appName = firstNonBlank(value(config, "targetAppName"), value(config, "appServiceName"));
        if (resourceGroup.isBlank() || appName.isBlank()) {
            return TargetAccessValidation.failure(
                "Target execution metadata is missing App Service deployment fields.",
                DefaultTargetAccessResolver.invalidTargetConfiguration(
                    "Target execution metadata is missing App Service deployment fields.",
                    "executionConfig.targetResourceGroup/resourceGroup or executionConfig.targetAppName/appServiceName is blank",
                    context.containerAppResourceId()
                )
            );
        }

        AzureWorkloadRbacAccessStrategyConfig accessConfig = (AzureWorkloadRbacAccessStrategyConfig) project.accessStrategyConfig();
        return TargetAccessValidation.success(
            "Validated target " + target.id() + " for Azure DevOps pipeline execution.",
            new AzureWorkloadRbacTargetAccessContext(
                context.tenantId().toString(),
                context.subscriptionId().toString(),
                normalize(accessConfig.authModel())
            )
        );
    }

    private String value(Map<String, String> values, String key) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return normalize(values.get(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!normalize(value).isBlank()) {
                return normalize(value);
            }
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
