package com.mappo.controlplane.api;

import com.mappo.controlplane.api.query.ProjectAuditPageParameters;
import com.mappo.controlplane.api.request.ProjectAdoPipelineDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoServiceConnectionDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectConfigurationPatchRequest;
import com.mappo.controlplane.api.request.ProjectCreateRequest;
import com.mappo.controlplane.api.request.ProjectValidationRequest;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ProjectAdoPipelineDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoServiceConnectionDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectConfigurationAuditPageRecord;
import com.mappo.controlplane.model.ProjectValidationResultRecord;
import com.mappo.controlplane.service.project.ProjectAuditQueryService;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import com.mappo.controlplane.service.project.ProjectConfigurationCommandService;
import com.mappo.controlplane.service.project.ProjectDeploymentDriverDiscoveryService;
import com.mappo.controlplane.service.project.ProjectValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project definitions and execution-capability configuration.")
public class ProjectsController {

    private final ProjectCatalogService projectCatalogService;
    private final ProjectConfigurationCommandService projectConfigurationCommandService;
    private final ProjectValidationService projectValidationService;
    private final ProjectAuditQueryService projectAuditQueryService;
    private final ProjectDeploymentDriverDiscoveryService projectDeploymentDriverDiscoveryService;

    @GetMapping
    @Operation(summary = "List projects", description = "Returns the configured projects available to the control plane.")
    public List<ProjectDefinition> listProjects() {
        return projectCatalogService.listProjects();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create project", description = "Creates a new project definition including access strategy, deployment driver, release source, and runtime health provider settings.")
    public ProjectDefinition createProject(@Valid @RequestBody ProjectCreateRequest request) {
        return projectConfigurationCommandService.createProject(request);
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Patch project configuration", description = "Updates project display name and config objects used by access, driver, release source, and runtime health providers.")
    public ProjectDefinition patchProjectConfiguration(
        @PathVariable("projectId") String projectId,
        @RequestBody(required = false) ProjectConfigurationPatchRequest patchRequest
    ) {
        return projectConfigurationCommandService.patchProjectConfiguration(projectId, patchRequest);
    }

    @PostMapping("/{projectId}/validate")
    @Operation(summary = "Validate project configuration", description = "Runs credential, webhook, and target-contract checks for the selected project.")
    public ProjectValidationResultRecord validateProjectConfiguration(
        @PathVariable("projectId") String projectId,
        @RequestBody(required = false) ProjectValidationRequest request
    ) {
        return projectValidationService.validateProject(projectId, request);
    }

    @GetMapping("/{projectId}/audit")
    @Operation(summary = "List project configuration audit events", description = "Returns paginated project configuration mutation audit history.")
    public ProjectConfigurationAuditPageRecord listProjectAudit(
        @PathVariable("projectId") String projectId,
        @Valid @ParameterObject @ModelAttribute ProjectAuditPageParameters parameters
    ) {
        return projectAuditQueryService.listProjectAudit(projectId, parameters.toQuery(projectId));
    }

    @PostMapping("/{projectId}/deployment-driver/ado/pipelines/discover")
    @Operation(
        summary = "Discover Azure DevOps pipelines",
        description = "Discovers Azure DevOps pipelines for the selected project using organization/project/PAT from request overrides or persisted project deployment-driver config."
    )
    public ProjectAdoPipelineDiscoveryResultRecord discoverProjectAdoPipelines(
        @PathVariable("projectId") String projectId,
        @RequestBody(required = false) ProjectAdoPipelineDiscoveryRequest request
    ) {
        return projectDeploymentDriverDiscoveryService.discoverAdoPipelines(projectId, request);
    }

    @PostMapping("/{projectId}/deployment-driver/ado/service-connections/discover")
    @Operation(
        summary = "Discover Azure DevOps service connections",
        description = "Discovers Azure DevOps service connections for the selected project using organization/project/PAT from request overrides or persisted project deployment-driver config."
    )
    public ProjectAdoServiceConnectionDiscoveryResultRecord discoverProjectAdoServiceConnections(
        @PathVariable("projectId") String projectId,
        @RequestBody(required = false) ProjectAdoServiceConnectionDiscoveryRequest request
    ) {
        return projectDeploymentDriverDiscoveryService.discoverAdoServiceConnections(projectId, request);
    }
}
