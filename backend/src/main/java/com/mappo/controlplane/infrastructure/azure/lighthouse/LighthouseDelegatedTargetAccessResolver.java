package com.mappo.controlplane.infrastructure.azure.lighthouse;

import com.mappo.controlplane.domain.access.LighthouseDelegatedTargetAccessContext;
import com.mappo.controlplane.domain.access.TargetAccessResolver;
import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.integrations.azure.access.config.LighthouseDelegatedAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.run.DefaultTargetAccessResolver;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class LighthouseDelegatedTargetAccessResolver implements TargetAccessResolver {

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured) {
        return project.accessStrategy() == ProjectAccessStrategyType.lighthouse_delegated_access;
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
                "Target is missing registration metadata required for delegated execution.",
                DefaultTargetAccessResolver.invalidTargetConfiguration(
                    "Target is missing registration metadata required for delegated execution.",
                    null,
                    null
                )
            );
        }
        LighthouseDelegatedAccessStrategyConfig config = (LighthouseDelegatedAccessStrategyConfig) project.accessStrategyConfig();
        if (blank(config.managingTenantId()) || blank(config.managingPrincipalClientId()) || blank(config.azureServiceConnectionName())) {
            return TargetAccessValidation.failure(
                "Project is missing Lighthouse delegated-access configuration required for execution.",
                DefaultTargetAccessResolver.invalidTargetConfiguration(
                    "Project is missing Lighthouse delegated-access configuration required for execution.",
                    "managingTenantId, managingPrincipalClientId, or azureServiceConnectionName is blank",
                    context.containerAppResourceId()
                )
            );
        }
        return TargetAccessValidation.success(
            "Validated target " + target.id() + " for Lighthouse delegated execution.",
            new LighthouseDelegatedTargetAccessContext(
                context.tenantId() == null ? "" : context.tenantId().toString(),
                context.subscriptionId() == null ? "" : context.subscriptionId().toString(),
                config.managingTenantId(),
                config.managingPrincipalClientId(),
                config.azureServiceConnectionName()
            )
        );
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
