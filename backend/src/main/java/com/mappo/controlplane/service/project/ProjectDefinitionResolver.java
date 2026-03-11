package com.mappo.controlplane.service.project;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectDefinitionResolver {

    private final ProjectCatalogService projectCatalogService;

    public ProjectDefinition resolve(ReleaseRecord release) {
        return projectCatalogService.getRequired(release.projectId());
    }
}
