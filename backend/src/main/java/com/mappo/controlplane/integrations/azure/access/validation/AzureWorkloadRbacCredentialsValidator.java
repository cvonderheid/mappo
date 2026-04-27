package com.mappo.controlplane.integrations.azure.access.validation;

import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.fail;
import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.pass;

import com.mappo.controlplane.application.project.validation.ProjectCredentialsValidator;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.integrations.azure.access.config.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.ProjectValidationScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureWorkloadRbacCredentialsValidator implements ProjectCredentialsValidator {

    private final MappoProperties properties;

    @Override
    public boolean supports(ProjectDefinition project) {
        return project.accessStrategyConfig() instanceof AzureWorkloadRbacAccessStrategyConfig;
    }

    @Override
    public List<ProjectValidationFindingRecord> validate(ProjectDefinition project) {
        AzureWorkloadRbacAccessStrategyConfig accessConfig = (AzureWorkloadRbacAccessStrategyConfig) project.accessStrategyConfig();
        if (!accessConfig.requiresAzureCredential()) {
            return List.of(pass(
                ProjectValidationScope.credentials,
                "AZURE_CREDENTIALS_NOT_REQUIRED",
                "Access strategy does not require MAPPO-managed Azure credentials."
            ));
        }
        boolean runtimeConfigured = hasText(properties.getAzure().getTenantId());
        if (runtimeConfigured) {
            return List.of(pass(
                ProjectValidationScope.credentials,
                "AZURE_CREDENTIALS_PRESENT",
                "Azure DefaultAzureCredential is configured for this runtime."
            ));
        }
        return List.of(fail(
            ProjectValidationScope.credentials,
            "AZURE_CREDENTIALS_MISSING",
            "Azure runtime identity is not configured. Set MAPPO_AZURE_TENANT_ID or run MAPPO with managed identity in Azure."
        ));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
