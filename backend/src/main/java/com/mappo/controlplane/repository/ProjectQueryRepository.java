package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROJECTS;

import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProjectQueryRepository {

    private final DSLContext dsl;

    public Optional<ProjectDefinition> getProject(String projectId) {
        Record row = dsl.select(
                PROJECTS.ID,
                PROJECTS.NAME,
                PROJECTS.ACCESS_STRATEGY,
                PROJECTS.DEPLOYMENT_DRIVER,
                PROJECTS.RELEASE_ARTIFACT_SOURCE,
                PROJECTS.RUNTIME_HEALTH_PROVIDER
            )
            .from(PROJECTS)
            .where(PROJECTS.ID.eq(projectId))
            .fetchOne();

        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toProjectDefinition(row));
    }

    public List<ProjectDefinition> listProjects() {
        return dsl.select(
                PROJECTS.ID,
                PROJECTS.NAME,
                PROJECTS.ACCESS_STRATEGY,
                PROJECTS.DEPLOYMENT_DRIVER,
                PROJECTS.RELEASE_ARTIFACT_SOURCE,
                PROJECTS.RUNTIME_HEALTH_PROVIDER
            )
            .from(PROJECTS)
            .orderBy(PROJECTS.ID.asc())
            .fetch(this::toProjectDefinition);
    }

    private ProjectDefinition toProjectDefinition(Record row) {
        return new ProjectDefinition(
            row.get(PROJECTS.ID),
            row.get(PROJECTS.NAME),
            ProjectAccessStrategyType.valueOf(row.get(PROJECTS.ACCESS_STRATEGY).getLiteral()),
            ProjectDeploymentDriverType.valueOf(row.get(PROJECTS.DEPLOYMENT_DRIVER).getLiteral()),
            ProjectReleaseArtifactSourceType.valueOf(row.get(PROJECTS.RELEASE_ARTIFACT_SOURCE).getLiteral()),
            ProjectRuntimeHealthProviderType.valueOf(row.get(PROJECTS.RUNTIME_HEALTH_PROVIDER).getLiteral())
        );
    }
}
