package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.ProjectConfigurationPatchRequest;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import com.mappo.controlplane.service.project.ProjectConfigurationCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project definitions and execution-capability configuration.")
public class ProjectsController {

    private final ProjectCatalogService projectCatalogService;
    private final ProjectConfigurationCommandService projectConfigurationCommandService;

    @GetMapping
    @Operation(summary = "List projects", description = "Returns the configured projects available to the control plane.")
    public List<ProjectDefinition> listProjects() {
        return projectCatalogService.listProjects();
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Patch project configuration", description = "Updates project display name and config objects used by access, driver, release source, and runtime health providers.")
    public ProjectDefinition patchProjectConfiguration(
        @PathVariable("projectId") String projectId,
        @RequestBody(required = false) ProjectConfigurationPatchRequest patchRequest
    ) {
        return projectConfigurationCommandService.patchProjectConfiguration(projectId, patchRequest);
    }
}
