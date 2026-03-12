package com.mappo.controlplane.infrastructure.azure.templatespec;

import com.mappo.controlplane.domain.access.AzureWorkloadRbacTargetAccessContext;
import com.mappo.controlplane.domain.access.TargetAccessResolver;
import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.domain.project.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.run.DefaultTargetAccessResolver;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class TemplateSpecTargetAccessResolver implements TargetAccessResolver {

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured) {
        return project.accessStrategy() == ProjectAccessStrategyType.azure_workload_rbac
            && project.deploymentDriver() == ProjectDeploymentDriverType.azure_template_spec;
    }

    @Override
    public TargetAccessValidation validate(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
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

        if (azureConfigured && release.deploymentScope() == MappoDeploymentScope.resource_group) {
            if (blank(context.managedResourceGroupId())) {
                return TargetAccessValidation.failure(
                    "Target is missing managedResourceGroupId required for Template Spec execution.",
                    DefaultTargetAccessResolver.invalidTargetConfiguration(
                        "Target is missing managedResourceGroupId required for Template Spec execution.",
                        "managedResourceGroupId is blank",
                        context.containerAppResourceId()
                    )
                );
            }
            return TargetAccessValidation.success(
                "Validated target " + target.id() + "; deploying into resource group " + resourceGroupName(context.managedResourceGroupId()) + ".",
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

    private String resourceGroupName(String resourceId) {
        String value = resourceId == null ? "" : resourceId.trim();
        int index = value.toLowerCase().indexOf("/resourcegroups/");
        if (index < 0) {
            return value;
        }
        String suffix = value.substring(index + "/resourceGroups/".length());
        int slash = suffix.indexOf('/');
        return slash < 0 ? suffix : suffix.substring(0, slash);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
