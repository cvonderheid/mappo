package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProjectAdoPipelineDiscoveryRequest;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsClientException;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDefinitionRecord;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsPipelineDiscoveryService;
import com.mappo.controlplane.model.ProjectAdoPipelineDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoPipelineRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectDeploymentDriverDiscoveryService {

    private final ProjectCatalogService projectCatalogService;
    private final AzureDevOpsPipelineDiscoveryService pipelineDiscoveryService;

    public ProjectDeploymentDriverDiscoveryService(
        ProjectCatalogService projectCatalogService,
        AzureDevOpsPipelineDiscoveryService pipelineDiscoveryService
    ) {
        this.projectCatalogService = projectCatalogService;
        this.pipelineDiscoveryService = pipelineDiscoveryService;
    }

    public ProjectAdoPipelineDiscoveryResultRecord discoverAdoPipelines(
        String projectId,
        ProjectAdoPipelineDiscoveryRequest request
    ) {
        ProjectDefinition definition = projectCatalogService.getRequired(projectId);
        if (definition.deploymentDriver() != ProjectDeploymentDriverType.pipeline_trigger) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Project deployment driver must be pipeline_trigger for Azure DevOps pipeline discovery."
            );
        }

        if (!(definition.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig config)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Project deployment driver configuration is not pipeline_trigger-compatible."
            );
        }

        String pipelineSystem = normalize(config.pipelineSystem());
        if (!"azure_devops".equalsIgnoreCase(pipelineSystem)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Project pipeline system must be azure_devops for discovery."
            );
        }

        String organization = firstNonBlank(
            request == null ? null : request.organization(),
            config.organization()
        );
        String adoProject = firstNonBlank(
            request == null ? null : request.project(),
            config.project()
        );
        String tokenReference = firstNonBlank(
            request == null ? null : request.personalAccessTokenRef(),
            config.personalAccessTokenRef()
        );
        String nameContains = normalize(request == null ? null : request.nameContains());

        if (organization.isBlank() || adoProject.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "organization and project are required to discover Azure DevOps pipelines."
            );
        }

        try {
            List<ProjectAdoPipelineRecord> pipelines = pipelineDiscoveryService
                .discoverPipelines(organization, adoProject, tokenReference)
                .stream()
                .filter(pipeline -> nameContains.isBlank()
                    || normalize(pipeline.name()).toLowerCase(Locale.ROOT).contains(nameContains.toLowerCase(Locale.ROOT)))
                .sorted(Comparator
                    .comparing((AzureDevOpsPipelineDefinitionRecord pipeline) -> normalize(pipeline.name()).toLowerCase(Locale.ROOT))
                    .thenComparing(pipeline -> normalize(pipeline.id())))
                .map(pipeline -> new ProjectAdoPipelineRecord(
                    normalize(pipeline.id()),
                    normalize(pipeline.name()),
                    normalize(pipeline.folder()),
                    normalize(pipeline.webUrl())
                ))
                .toList();
            return new ProjectAdoPipelineDiscoveryResultRecord(
                definition.id(),
                organization,
                adoProject,
                pipelines
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AzureDevOpsClientException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Azure DevOps pipeline discovery failed: " + normalize(exception.responseBody())
            );
        }
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
