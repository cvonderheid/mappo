package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProjectConfigurationPatchRequest;
import com.mappo.controlplane.api.request.ProjectCreateRequest;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ProjectConfigurationAuditAction;
import com.mappo.controlplane.model.command.ProjectConfigurationAuditCommand;
import com.mappo.controlplane.persistence.project.ProjectConfigurationAuditRepository;
import com.mappo.controlplane.persistence.project.ProjectCommandRepository;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointCatalogService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
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
    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ProviderConnectionCatalogService providerConnectionCatalogService;

    @Transactional
    public ProjectDefinition createProject(ProjectCreateRequest request) {
        ProjectConfigurationMutationRecord mutation = projectConfigurationMutationService.fromCreate(
            generatedProjectId(request),
            request
        );
        validateReleaseIngestEndpointReference(mutation.releaseIngestEndpointId());
        validateProviderConnectionReference(mutation.providerConnectionId());
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
        validateReleaseIngestEndpointReference(mutation.releaseIngestEndpointId());
        validateProviderConnectionReference(mutation.providerConnectionId());
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

    @Transactional
    public void deleteProject(String projectId) {
        ProjectDefinition current = projectCatalogService.getRequired(projectId);
        projectCommandRepository.deleteProjectCascade(current.id());
    }

    public Map<String, Object> snapshot(String projectId) {
        ProjectDefinition project = projectCatalogService.getRequired(projectId);
        return projectConfigurationMutationService.snapshot(project);
    }

    private String newAuditId() {
        return "pca-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String generatedProjectId(ProjectCreateRequest request) {
        return nextAvailableProjectId(request.name());
    }

    private String nextAvailableProjectId(String projectName) {
        String base = slugifyProjectId(projectName);
        List<String> existingProjectIds = projectCatalogService.listProjects().stream()
            .map(ProjectDefinition::id)
            .toList();
        for (int suffix = 0; suffix < 1000; suffix++) {
            String candidate = suffix == 0 ? base : base + "-" + suffix;
            if (!existingProjectIds.contains(candidate)) {
                return candidate;
            }
        }
        return base + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String slugifyProjectId(String value) {
        String normalized = normalize(value)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            return "project";
        }
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80).replaceAll("-+$", "");
        }
        if (normalized.length() < 3) {
            normalized = normalized + "-project";
        }
        return normalized.isBlank() ? "project" : normalized;
    }

    private void validateReleaseIngestEndpointReference(String endpointId) {
        String normalized = normalize(endpointId);
        if (normalized.isBlank()) {
            return;
        }
        if (!releaseIngestEndpointCatalogService.exists(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source not found: " + normalized);
        }
    }

    private void validateProviderConnectionReference(String connectionId) {
        String normalized = normalize(connectionId);
        if (normalized.isBlank()) {
            return;
        }
        if (!providerConnectionCatalogService.exists(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "deployment connection not found: " + normalized);
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
