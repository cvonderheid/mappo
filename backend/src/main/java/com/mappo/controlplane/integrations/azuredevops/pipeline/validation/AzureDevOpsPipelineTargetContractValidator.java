package com.mappo.controlplane.integrations.azuredevops.pipeline.validation;

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
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AzureDevOpsPipelineTargetContractValidator implements ProjectTargetContractValidator {

    @Override
    public boolean supports(ProjectDefinition project) {
        return project.deploymentDriver() == ProjectDeploymentDriverType.pipeline_trigger;
    }

    @Override
    public List<ProjectValidationFindingRecord> validate(
        ProjectDefinition project,
        TargetRecord target,
        TargetExecutionContextRecord context
    ) {
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();
        String resourceGroup = firstNonBlank(value(context.executionConfig(), "targetResourceGroup"), value(context.executionConfig(), "resourceGroup"));
        String appName = firstNonBlank(value(context.executionConfig(), "targetAppName"), value(context.executionConfig(), "appServiceName"));
        if (resourceGroup.isBlank() || appName.isBlank()) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "PIPELINE_TARGET_METADATA_MISSING",
                "Target " + target.id() + " must define executionConfig.resourceGroup/targetResourceGroup and executionConfig.appServiceName/targetAppName."
            ));
        }
        return findings;
    }

    private String value(Map<String, String> values, String key) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return normalize(values.get(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
