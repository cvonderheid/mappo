package com.mappo.controlplane.integrations.azure.templatespec.validation;

import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.fail;

import com.mappo.controlplane.application.project.validation.ProjectTargetContractValidator;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.ProjectValidationScope;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AzureTemplateSpecTargetContractValidator implements ProjectTargetContractValidator {

    @Override
    public boolean supports(ProjectDefinition project) {
        return project.deploymentDriver() == ProjectDeploymentDriverType.azure_template_spec;
    }

    @Override
    public List<ProjectValidationFindingRecord> validate(
        ProjectDefinition project,
        TargetRecord target,
        TargetExecutionContextRecord context
    ) {
        if (normalize(context.managedResourceGroupId()).isBlank()) {
            return List.of(fail(
                ProjectValidationScope.target_contract,
                "MANAGED_RESOURCE_GROUP_MISSING",
                "Target " + target.id() + " must define managedResourceGroupId for template-spec execution."
            ));
        }
        return List.of();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
