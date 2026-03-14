package com.mappo.controlplane.service.project;

import com.mappo.controlplane.model.ProjectConfigurationAuditPageRecord;
import com.mappo.controlplane.model.query.ProjectConfigurationAuditPageQuery;
import com.mappo.controlplane.repository.ProjectConfigurationAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectAuditQueryService {

    private final ProjectCatalogService projectCatalogService;
    private final ProjectConfigurationAuditRepository projectConfigurationAuditRepository;

    public ProjectConfigurationAuditPageRecord listProjectAudit(String projectId, ProjectConfigurationAuditPageQuery query) {
        String resolvedProjectId = projectCatalogService.resolveRequiredProjectId(projectId);
        ProjectConfigurationAuditPageQuery resolvedQuery = new ProjectConfigurationAuditPageQuery(
            query == null ? 0 : query.page(),
            query == null ? 25 : query.size(),
            resolvedProjectId,
            query == null ? null : query.action()
        );
        return projectConfigurationAuditRepository.listProjectAuditPage(resolvedQuery);
    }
}

