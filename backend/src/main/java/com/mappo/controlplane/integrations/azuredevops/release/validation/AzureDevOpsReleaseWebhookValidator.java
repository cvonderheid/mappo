package com.mappo.controlplane.integrations.azuredevops.release.validation;

import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.fail;
import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.pass;

import com.mappo.controlplane.application.project.validation.ProjectWebhookValidator;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.ProjectValidationScope;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointCatalogService;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestSecretResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDevOpsReleaseWebhookValidator implements ProjectWebhookValidator {

    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ReleaseIngestSecretResolver releaseIngestSecretResolver;

    @Override
    public boolean supports(ProjectDefinition project) {
        return project.releaseArtifactSourceConfig() instanceof ExternalDeploymentInputsArtifactSourceConfig config
            && "azure_devops".equalsIgnoreCase(normalize(config.sourceSystem()));
    }

    @Override
    public List<ProjectValidationFindingRecord> validate(ProjectDefinition project) {
        String endpointId = normalize(project.releaseIngestEndpointId());
        if (endpointId.isBlank()) {
            return List.of(fail(
                ProjectValidationScope.webhook,
                "AZURE_DEVOPS_WEBHOOK_SECRET_MISSING",
                "Azure DevOps webhook ingest requires a linked Azure DevOps release source with a resolvable webhook secret."
            ));
        }
        try {
            ReleaseIngestEndpointRecord endpoint = releaseIngestEndpointCatalogService.getRequired(endpointId);
            if (endpoint.provider() == null || !"azure_devops".equalsIgnoreCase(endpoint.provider().name())) {
                return List.of(fail(
                    ProjectValidationScope.webhook,
                    "AZURE_DEVOPS_WEBHOOK_ENDPOINT_MISSING",
                    "Linked release source " + endpointId + " is not an Azure DevOps release source."
                ));
            }
            if (!normalize(releaseIngestSecretResolver.resolveConfiguredSecret(endpoint)).isBlank()) {
                return List.of(pass(
                    ProjectValidationScope.webhook,
                    "AZURE_DEVOPS_WEBHOOK_SECRET_PRESENT",
                    "Azure DevOps webhook secret resolved from linked release source " + endpointId + "."
                ));
            }
            return List.of(fail(
                ProjectValidationScope.webhook,
                "AZURE_DEVOPS_WEBHOOK_SECRET_MISSING",
                "Linked release source " + endpointId + " has no resolvable webhook secret."
            ));
        } catch (RuntimeException exception) {
            return List.of(fail(
                ProjectValidationScope.webhook,
                "AZURE_DEVOPS_WEBHOOK_ENDPOINT_MISSING",
                "Release source " + endpointId + " was not found."
            ));
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
