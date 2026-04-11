package com.mappo.controlplane.integrations.azure.deploymentstack.validation;

import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.fail;

import com.mappo.controlplane.application.project.validation.ProjectTargetContractValidator;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.ProjectValidationScope;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AzureDeploymentStackTargetContractValidator implements ProjectTargetContractValidator {

    @Override
    public boolean supports(ProjectDefinition project) {
        return project.deploymentDriver() == ProjectDeploymentDriverType.azure_deployment_stack;
    }

    @Override
    public List<ProjectValidationFindingRecord> validate(
        ProjectDefinition project,
        TargetRecord target,
        TargetExecutionContextRecord context
    ) {
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();
        if (normalize(context.managedResourceGroupId()).isBlank()) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "MANAGED_RESOURCE_GROUP_MISSING",
                "Target " + target.id() + " must define managedResourceGroupId for deployment-stack execution."
            ));
        }
        if (normalize(context.deploymentStackName()).isBlank()) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "DEPLOYMENT_STACK_NAME_MISSING",
                "Target " + target.id() + " must define deploymentStackName for deployment-stack execution."
            ));
        }
        return findings;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
