package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProjectConfigurationPatchRequest;
import com.mappo.controlplane.api.request.ProjectCreateRequest;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ProjectConfigurationAuditAction;
import com.mappo.controlplane.model.command.ProjectConfigurationAuditCommand;
import com.mappo.controlplane.repository.ProjectConfigurationAuditRepository;
import com.mappo.controlplane.repository.ProjectCommandRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectConfigurationCommandService {

    private final ProjectCatalogService projectCatalogService;
    private final ProjectCommandRepository projectCommandRepository;
    private final ProjectConfigurationMutationService projectConfigurationMutationService;
    private final ProjectConfigurationAuditRepository projectConfigurationAuditRepository;

    @Transactional
    public ProjectDefinition createProject(ProjectCreateRequest request) {
        ProjectConfigurationMutationRecord mutation = projectConfigurationMutationService.fromCreate(request);
        try {
            projectCommandRepository.createProject(mutation);
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project already exists: " + mutation.id());
        }

        ProjectDefinition created = projectCatalogService.getRequired(mutation.id());
        projectConfigurationAuditRepository.saveAuditEvent(new ProjectConfigurationAuditCommand(
            newAuditId(),
            created.id(),
            ProjectConfigurationAuditAction.created,
            "api",
            "Created project configuration.",
            null,
            projectConfigurationMutationService.snapshot(created),
            OffsetDateTime.now(ZoneOffset.UTC)
        ));
        return created;
    }

    @Transactional
    public ProjectDefinition patchProjectConfiguration(String projectId, ProjectConfigurationPatchRequest patchRequest) {
        ProjectDefinition current = projectCatalogService.getRequired(projectId);
        if (patchRequest == null) {
            return current;
        }

        ProjectConfigurationMutationRecord mutation = projectConfigurationMutationService.fromPatch(current, patchRequest);
        projectCommandRepository.updateProjectConfiguration(mutation);

        ProjectDefinition updated = projectCatalogService.getRequired(current.id());
        projectConfigurationAuditRepository.saveAuditEvent(new ProjectConfigurationAuditCommand(
            newAuditId(),
            updated.id(),
            ProjectConfigurationAuditAction.updated,
            "api",
            "Updated project configuration.",
            projectConfigurationMutationService.snapshot(current),
            projectConfigurationMutationService.snapshot(updated),
            OffsetDateTime.now(ZoneOffset.UTC)
        ));
        return updated;
    }

    public Map<String, Object> snapshot(String projectId) {
        ProjectDefinition project = projectCatalogService.getRequired(projectId);
        return projectConfigurationMutationService.snapshot(project);
    }

    private String newAuditId() {
        return "pca-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
