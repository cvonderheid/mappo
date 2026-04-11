package com.mappo.controlplane.integrations.github.release.validation;

import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.fail;
import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.pass;
import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.warning;

import com.mappo.controlplane.application.project.validation.ProjectWebhookValidator;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
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
public class GithubReleaseWebhookValidator implements ProjectWebhookValidator {

    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ReleaseIngestSecretResolver releaseIngestSecretResolver;
    private final MappoProperties properties;

    @Override
    public boolean supports(ProjectDefinition project) {
        return !(project.releaseArtifactSourceConfig() instanceof ExternalDeploymentInputsArtifactSourceConfig config
            && "azure_devops".equalsIgnoreCase(normalize(config.sourceSystem())));
    }

    @Override
    public List<ProjectValidationFindingRecord> validate(ProjectDefinition project) {
        String endpointId = normalize(project.releaseIngestEndpointId());
        if (!endpointId.isBlank()) {
            try {
                ReleaseIngestEndpointRecord endpoint = releaseIngestEndpointCatalogService.getRequired(endpointId);
                if (endpoint.provider() != ReleaseIngestProviderType.github) {
                    return List.of(fail(
                        ProjectValidationScope.webhook,
                        "GITHUB_WEBHOOK_ENDPOINT_MISSING",
                        "Linked release source " + endpointId + " is not a GitHub release source."
                    ));
                }
                if (!normalize(releaseIngestSecretResolver.resolveConfiguredSecret(endpoint)).isBlank()) {
                    return List.of(pass(
                        ProjectValidationScope.webhook,
                        "GITHUB_WEBHOOK_SECRET_PRESENT",
                        "GitHub webhook secret resolved from linked release source " + endpointId + "."
                    ));
                }
                return List.of(warning(
                    ProjectValidationScope.webhook,
                    "GITHUB_WEBHOOK_SECRET_MISSING",
                    "Linked GitHub release source " + endpointId + " has no resolvable webhook secret."
                ));
            } catch (RuntimeException exception) {
                return List.of(fail(
                    ProjectValidationScope.webhook,
                    "GITHUB_WEBHOOK_ENDPOINT_MISSING",
                    "Release source " + endpointId + " was not found."
                ));
            }
        }
        if (!normalize(properties.getManagedAppRelease().getWebhookSecret()).isBlank()) {
            return List.of(pass(
                ProjectValidationScope.webhook,
                "MANAGED_APP_WEBHOOK_SECRET_PRESENT",
                "Managed-app release webhook secret is configured."
            ));
        }
        return List.of(warning(
            ProjectValidationScope.webhook,
            "MANAGED_APP_WEBHOOK_SECRET_MISSING",
            "Managed-app release webhook secret is not configured; release registration may rely on manual actions."
        ));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
