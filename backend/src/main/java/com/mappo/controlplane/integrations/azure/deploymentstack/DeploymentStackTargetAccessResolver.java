package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.mappo.controlplane.integrations.azure.access.AzureWorkloadRbacTargetAccessContext;
import com.mappo.controlplane.domain.access.TargetAccessResolver;
import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.integrations.azure.access.config.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.run.DefaultTargetAccessResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Primary
@Order(10)
public class DeploymentStackTargetAccessResolver implements TargetAccessResolver {

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return project.accessStrategy() == ProjectAccessStrategyType.azure_workload_rbac
            && project.deploymentDriver() == ProjectDeploymentDriverType.azure_deployment_stack;
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
                "Target is missing registration metadata required for execution.",
                DefaultTargetAccessResolver.invalidTargetConfiguration(
                    "Target is missing registration metadata required for execution.",
                    null,
                    null
                )
            );
        }
        AzureWorkloadRbacAccessStrategyConfig config = (AzureWorkloadRbacAccessStrategyConfig) project.accessStrategyConfig();

        if (runtimeConfigured && release.deploymentScope() == MappoDeploymentScope.resource_group) {
            if (blank(context.managedResourceGroupId()) || blank(context.containerAppResourceId())) {
                return TargetAccessValidation.failure(
                    "Target is missing execution metadata required for deployment_stack execution.",
                    DefaultTargetAccessResolver.invalidTargetConfiguration(
                        "Target is missing execution metadata required for deployment_stack execution.",
                        "managedResourceGroupId or containerAppResourceId is blank",
                        context.containerAppResourceId()
                    )
                );
            }
            return TargetAccessValidation.success(
                "Validated target " + target.id() + "; updating deployment stack scope " + context.managedResourceGroupId() + ".",
                new AzureWorkloadRbacTargetAccessContext(
                    context.tenantId() == null ? "" : context.tenantId().toString(),
                    context.subscriptionId() == null ? "" : context.subscriptionId().toString(),
                    config.authModel()
                )
            );
        }

        return TargetAccessValidation.success(
            "Validated target " + target.id() + " for simulator execution.",
            new AzureWorkloadRbacTargetAccessContext(
                context.tenantId() == null ? "" : context.tenantId().toString(),
                context.subscriptionId() == null ? "" : context.subscriptionId().toString(),
                config.authModel()
            )
        );
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
