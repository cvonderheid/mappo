package com.mappo.controlplane.api;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project definitions and execution-capability configuration.")
public class ProjectsController {

    private final ProjectCatalogService projectCatalogService;

    @GetMapping
    @Operation(summary = "List projects", description = "Returns the configured projects available to the control plane.")
    public List<ProjectDefinition> listProjects() {
        return projectCatalogService.listProjects();
    }
}
