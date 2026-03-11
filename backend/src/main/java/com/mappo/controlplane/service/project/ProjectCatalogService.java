package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.domain.project.BuiltinProjects;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.repository.ProjectQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectCatalogService {

    private final ProjectQueryRepository projectQueryRepository;

    public ProjectDefinition getRequired(String projectId) {
        String normalized = normalize(projectId);
        return projectQueryRepository.getProject(normalized)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "project not found: " + normalized));
    }

    public String resolveProjectId(String explicitProjectId, MappoReleaseSourceType sourceType) {
        String normalized = normalize(explicitProjectId);
        if (!normalized.isBlank()) {
            getRequired(normalized);
            return normalized;
        }
        String inferred = BuiltinProjects.defaultProjectIdFor(sourceType);
        getRequired(inferred);
        return inferred;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
