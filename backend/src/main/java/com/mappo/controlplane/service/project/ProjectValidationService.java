package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.request.ProjectValidationRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.integrations.azure.access.config.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.ProjectValidationFindingStatus;
import com.mappo.controlplane.model.ProjectValidationResultRecord;
import com.mappo.controlplane.model.ProjectValidationScope;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.persistence.target.TargetExecutionContextRepository;
import com.mappo.controlplane.persistence.target.TargetRecordQueryRepository;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionSecretResolver;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointCatalogService;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestSecretResolver;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectValidationService {

    private final ProjectCatalogService projectCatalogService;
    private final TargetRecordQueryRepository targetRecordQueryRepository;
    private final TargetExecutionContextRepository targetExecutionContextRepository;
    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionSecretResolver providerConnectionSecretResolver;
    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ReleaseIngestSecretResolver releaseIngestSecretResolver;
    private final MappoProperties properties;

    public ProjectValidationResultRecord validateProject(String projectId, ProjectValidationRequest request) {
        ProjectDefinition project = projectCatalogService.getRequired(projectId);
        Set<ProjectValidationScope> scopes = resolveScopes(request);
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();

        if (scopes.contains(ProjectValidationScope.credentials)) {
            findings.addAll(validateCredentials(project));
        }
        if (scopes.contains(ProjectValidationScope.webhook)) {
            findings.addAll(validateWebhook(project));
        }
        if (scopes.contains(ProjectValidationScope.target_contract)) {
            findings.addAll(validateTargetContract(project, request == null ? null : request.targetId()));
        }

        boolean valid = findings.stream().noneMatch(finding -> finding.status() == ProjectValidationFindingStatus.fail);
        return new ProjectValidationResultRecord(project.id(), valid, OffsetDateTime.now(ZoneOffset.UTC), findings);
    }

    private List<ProjectValidationFindingRecord> validateCredentials(ProjectDefinition project) {
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();

        if (project.accessStrategyConfig() instanceof AzureWorkloadRbacAccessStrategyConfig accessConfig) {
            if (accessConfig.requiresAzureCredential()) {
                boolean azureConfigured = hasText(properties.getAzure().getTenantId())
                    && hasText(properties.getAzure().getClientId())
                    && hasText(properties.getAzure().getClientSecret());
                if (azureConfigured) {
                    findings.add(pass(
                        ProjectValidationScope.credentials,
                        "AZURE_CREDENTIALS_PRESENT",
                        "Azure service principal credentials are configured."
                    ));
                } else {
                    findings.add(fail(
                        ProjectValidationScope.credentials,
                        "AZURE_CREDENTIALS_MISSING",
                        "Azure service principal credentials are required but not fully configured (tenantId/clientId/clientSecret)."
                    ));
                }
            } else {
                findings.add(pass(
                    ProjectValidationScope.credentials,
                    "AZURE_CREDENTIALS_NOT_REQUIRED",
                    "Access strategy does not require MAPPO-managed Azure credentials."
                ));
            }
        } else {
            findings.add(warning(
                ProjectValidationScope.credentials,
                "ACCESS_STRATEGY_UNCHECKED",
                "Credential validation has no strategy-specific checks for this access strategy."
            ));
        }

        if (project.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig pipelineConfig
            && "azure_devops".equalsIgnoreCase(normalize(pipelineConfig.pipelineSystem()))) {
            String providerConnectionId = normalize(project.providerConnectionId());
            if (!hasText(providerConnectionId)) {
                findings.add(fail(
                    ProjectValidationScope.credentials,
                    "AZURE_DEVOPS_PAT_MISSING",
                    "Azure DevOps deployment driver requires a linked Azure DevOps deployment connection with a resolvable PAT."
                ));
            } else {
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

            if (hasText(pipelineConfig.organization())
                && hasText(pipelineConfig.project())
                && hasText(pipelineConfig.pipelineId())
                && hasText(pipelineConfig.azureServiceConnectionName())) {
                findings.add(pass(
                    ProjectValidationScope.credentials,
                    "AZURE_DEVOPS_PIPELINE_CONFIG_PRESENT",
                    "Azure DevOps pipeline configuration fields are populated."
                ));
            } else {
                findings.add(fail(
                    ProjectValidationScope.credentials,
                    "AZURE_DEVOPS_PIPELINE_CONFIG_MISSING",
                    "Azure DevOps pipeline configuration must include organization, project, pipelineId, and azureServiceConnectionName."
                ));
            }
        }

        return findings;
    }

    private List<ProjectValidationFindingRecord> validateWebhook(ProjectDefinition project) {
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();

        if (project.releaseArtifactSourceConfig() instanceof ExternalDeploymentInputsArtifactSourceConfig externalConfig
            && "azure_devops".equalsIgnoreCase(normalize(externalConfig.sourceSystem()))) {
            String endpointId = normalize(project.releaseIngestEndpointId());
            if (!hasText(endpointId)) {
                findings.add(fail(
                    ProjectValidationScope.webhook,
                    "AZURE_DEVOPS_WEBHOOK_SECRET_MISSING",
                    "Azure DevOps webhook ingest requires a linked Azure DevOps release source with a resolvable webhook secret."
                ));
                return findings;
            }
            try {
                ReleaseIngestEndpointRecord endpoint = releaseIngestEndpointCatalogService.getRequired(endpointId);
                if (endpoint.provider() == null || !"azure_devops".equalsIgnoreCase(endpoint.provider().name())) {
                    findings.add(fail(
                        ProjectValidationScope.webhook,
                        "AZURE_DEVOPS_WEBHOOK_ENDPOINT_MISSING",
                        "Linked release source " + endpointId + " is not an Azure DevOps release source."
                    ));
                    return findings;
                }
                if (hasText(releaseIngestSecretResolver.resolveConfiguredSecret(endpoint))) {
                    findings.add(pass(
                        ProjectValidationScope.webhook,
                        "AZURE_DEVOPS_WEBHOOK_SECRET_PRESENT",
                        "Azure DevOps webhook secret resolved from linked release source " + endpointId + "."
                    ));
                } else {
                    findings.add(fail(
                        ProjectValidationScope.webhook,
                        "AZURE_DEVOPS_WEBHOOK_SECRET_MISSING",
                        "Linked release source " + endpointId + " has no resolvable webhook secret."
                    ));
                }
            } catch (RuntimeException exception) {
                findings.add(fail(
                    ProjectValidationScope.webhook,
                    "AZURE_DEVOPS_WEBHOOK_ENDPOINT_MISSING",
                    "Release source " + endpointId + " was not found."
                ));
            }
            return findings;
        }

        if (hasText(properties.getManagedAppRelease().getWebhookSecret())) {
            findings.add(pass(
                ProjectValidationScope.webhook,
                "MANAGED_APP_WEBHOOK_SECRET_PRESENT",
                "Managed-app release webhook secret is configured."
            ));
        } else {
            findings.add(warning(
                ProjectValidationScope.webhook,
                "MANAGED_APP_WEBHOOK_SECRET_MISSING",
                "Managed-app release webhook secret is not configured; release registration may rely on manual actions."
            ));
        }
        return findings;
    }

    private List<ProjectValidationFindingRecord> validateTargetContract(ProjectDefinition project, String requestedTargetId) {
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();
        Optional<TargetRecord> maybeTarget = resolveTarget(project.id(), requestedTargetId);
        if (maybeTarget.isEmpty()) {
            findings.add(warning(
                ProjectValidationScope.target_contract,
                "NO_TARGETS_AVAILABLE",
                "No targets are registered for this project; target contract validation skipped."
            ));
            return findings;
        }

        TargetRecord target = maybeTarget.get();
        Optional<TargetExecutionContextRecord> context = targetExecutionContextRepository
            .getExecutionContextsByIds(List.of(target.id()))
            .stream()
            .findFirst();
        if (context.isEmpty()) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "TARGET_CONTEXT_MISSING",
                "Target " + target.id() + " is missing execution context metadata."
            ));
            return findings;
        }

        TargetExecutionContextRecord record = context.get();
        if (record.tenantId() == null || record.subscriptionId() == null) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "TARGET_TENANT_SUBSCRIPTION_MISSING",
                "Target " + target.id() + " is missing tenantId/subscriptionId metadata."
            ));
        }

        switch (project.deploymentDriver()) {
            case pipeline_trigger -> validatePipelineTargetContract(target, record, findings);
            case azure_deployment_stack -> validateDeploymentStackTargetContract(target, record, findings);
            case azure_template_spec -> validateTemplateSpecTargetContract(target, record, findings);
        }

        if (findings.stream().noneMatch(item -> item.status() == ProjectValidationFindingStatus.fail)) {
            findings.add(pass(
                ProjectValidationScope.target_contract,
                "TARGET_CONTRACT_VALID",
                "Target " + target.id() + " satisfies required execution metadata for " + project.deploymentDriver() + "."
            ));
        }
        return findings;
    }

    private void validatePipelineTargetContract(
        TargetRecord target,
        TargetExecutionContextRecord record,
        List<ProjectValidationFindingRecord> findings
    ) {
        String resourceGroup = firstNonBlank(value(record.executionConfig(), "targetResourceGroup"), value(record.executionConfig(), "resourceGroup"));
        String appName = firstNonBlank(value(record.executionConfig(), "targetAppName"), value(record.executionConfig(), "appServiceName"));
        if (!hasText(resourceGroup) || !hasText(appName)) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "PIPELINE_TARGET_METADATA_MISSING",
                "Target " + target.id() + " must define executionConfig.resourceGroup/targetResourceGroup and executionConfig.appServiceName/targetAppName."
            ));
        }
    }

    private void validateDeploymentStackTargetContract(
        TargetRecord target,
        TargetExecutionContextRecord record,
        List<ProjectValidationFindingRecord> findings
    ) {
        if (!hasText(record.managedResourceGroupId())) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "MANAGED_RESOURCE_GROUP_MISSING",
                "Target " + target.id() + " must define managedResourceGroupId for deployment-stack execution."
            ));
        }
        if (!hasText(record.deploymentStackName())) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "DEPLOYMENT_STACK_NAME_MISSING",
                "Target " + target.id() + " must define deploymentStackName for deployment-stack execution."
            ));
        }
    }

    private void validateTemplateSpecTargetContract(
        TargetRecord target,
        TargetExecutionContextRecord record,
        List<ProjectValidationFindingRecord> findings
    ) {
        if (!hasText(record.managedResourceGroupId())) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "MANAGED_RESOURCE_GROUP_MISSING",
                "Target " + target.id() + " must define managedResourceGroupId for template-spec execution."
            ));
        }
    }

    private Optional<TargetRecord> resolveTarget(String projectId, String requestedTargetId) {
        String targetId = normalize(requestedTargetId);
        if (hasText(targetId)) {
            return targetRecordQueryRepository.getTarget(targetId)
                .filter(target -> projectId.equals(target.projectId()));
        }
        List<TargetRecord> targets = targetRecordQueryRepository.getTargetsByTagFiltersForProject(Map.of(), projectId);
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(targets.get(0));
    }

    private Set<ProjectValidationScope> resolveScopes(ProjectValidationRequest request) {
        if (request == null || request.scopes() == null || request.scopes().isEmpty()) {
            return Set.of(ProjectValidationScope.credentials, ProjectValidationScope.webhook, ProjectValidationScope.target_contract);
        }
        Set<ProjectValidationScope> scopes = new LinkedHashSet<>();
        for (ProjectValidationScope scope : request.scopes()) {
            if (scope != null) {
                scopes.add(scope);
            }
        }
        if (scopes.isEmpty()) {
            return Set.of(ProjectValidationScope.credentials, ProjectValidationScope.webhook, ProjectValidationScope.target_contract);
        }
        return scopes;
    }

    private ProjectValidationFindingRecord pass(ProjectValidationScope scope, String code, String message) {
        return new ProjectValidationFindingRecord(scope, ProjectValidationFindingStatus.pass, code, message);
    }

    private ProjectValidationFindingRecord warning(ProjectValidationScope scope, String code, String message) {
        return new ProjectValidationFindingRecord(scope, ProjectValidationFindingStatus.warning, code, message);
    }

    private ProjectValidationFindingRecord fail(ProjectValidationScope scope, String code, String message) {
        return new ProjectValidationFindingRecord(scope, ProjectValidationFindingStatus.fail, code, message);
    }

    private String value(Map<String, String> values, String key) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return normalize(values.get(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return normalize(value);
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return !normalize(value).isBlank();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

}
