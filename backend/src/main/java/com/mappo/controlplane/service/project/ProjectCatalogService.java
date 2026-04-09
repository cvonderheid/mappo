package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.persistence.project.ProjectQueryRepository;
import java.util.List;
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

    public String resolveRequiredProjectId(String explicitProjectId) {
        String normalized = normalize(explicitProjectId);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        getRequired(normalized);
        return normalized;
    }

    public List<ProjectDefinition> listProjects() {
        return projectQueryRepository.listProjects();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
