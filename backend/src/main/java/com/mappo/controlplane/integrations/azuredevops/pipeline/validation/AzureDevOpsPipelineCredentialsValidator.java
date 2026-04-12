package com.mappo.controlplane.integrations.azuredevops.pipeline.validation;

import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.fail;
import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.pass;

import com.mappo.controlplane.application.project.validation.ProjectCredentialsValidator;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.PipelineTriggerDriverConfig;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.ProjectValidationScope;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionSecretResolver;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDevOpsPipelineCredentialsValidator implements ProjectCredentialsValidator {

    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionSecretResolver providerConnectionSecretResolver;

    @Override
    public boolean supports(ProjectDefinition project) {
        return project.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig config
            && "azure_devops".equalsIgnoreCase(normalize(config.pipelineSystem()));
    }

    @Override
    public List<ProjectValidationFindingRecord> validate(ProjectDefinition project) {
        PipelineTriggerDriverConfig pipelineConfig = (PipelineTriggerDriverConfig) project.deploymentDriverConfig();
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();
        String providerConnectionId = normalize(project.providerConnectionId());
        if (providerConnectionId.isBlank()) {
            findings.add(fail(
                ProjectValidationScope.credentials,
                "AZURE_DEVOPS_PAT_MISSING",
                "Azure DevOps deployment driver requires a linked Azure DevOps deployment connection with a resolvable PAT."
            ));
        } else {
            validatePat(providerConnectionId, findings);
        }

        if (hasText(pipelineConfig.organization())
            && hasText(pipelineConfig.project())
            && hasText(pipelineConfig.pipelineId())) {
            findings.add(pass(
                ProjectValidationScope.credentials,
                "AZURE_DEVOPS_PIPELINE_CONFIG_PRESENT",
                "Azure DevOps pipeline configuration fields are populated."
            ));
        } else {
            findings.add(fail(
                ProjectValidationScope.credentials,
                "AZURE_DEVOPS_PIPELINE_CONFIG_MISSING",
                "Azure DevOps pipeline configuration must include organization, project, and pipelineId."
            ));
        }
        return findings;
    }

    private void validatePat(String providerConnectionId, List<ProjectValidationFindingRecord> findings) {
        try {
            ProviderConnectionRecord connection = providerConnectionCatalogService.getRequired(providerConnectionId);
            if (connection.provider() == null || !"azure_devops".equalsIgnoreCase(connection.provider().name())) {
                findings.add(fail(
                    ProjectValidationScope.credentials,
                    "AZURE_DEVOPS_PAT_MISSING",
                    "Linked deployment connection " + providerConnectionId + " is not an Azure DevOps connection."
                ));
            } else if (hasText(providerConnectionSecretResolver.resolvePersonalAccessToken(connection))) {
                findings.add(pass(
                    ProjectValidationScope.credentials,
                    "AZURE_DEVOPS_PAT_PRESENT",
                    "Azure DevOps personal access token resolved from linked deployment connection " + providerConnectionId + "."
                ));
            } else {
                findings.add(fail(
                    ProjectValidationScope.credentials,
                    "AZURE_DEVOPS_PAT_MISSING",
                    "Linked deployment connection " + providerConnectionId + " does not resolve an Azure DevOps PAT."
                ));
            }
        } catch (RuntimeException exception) {
            findings.add(fail(
                ProjectValidationScope.credentials,
                "AZURE_DEVOPS_PAT_MISSING",
                "Deployment connection " + providerConnectionId + " was not found."
            ));
        }
    }

    private boolean hasText(String value) {
        return !normalize(value).isBlank();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
