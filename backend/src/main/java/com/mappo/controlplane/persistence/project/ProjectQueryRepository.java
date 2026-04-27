package com.mappo.controlplane.persistence.project;

import static com.mappo.controlplane.jooq.Tables.PROJECTS;

import com.mappo.controlplane.application.project.config.ProjectAccessStrategyConfigRegistry;
import com.mappo.controlplane.application.project.config.ProjectDeploymentDriverConfigRegistry;
import com.mappo.controlplane.application.project.config.ProjectReleaseArtifactSourceConfigRegistry;
import com.mappo.controlplane.application.project.config.ProjectRuntimeHealthProviderConfigRegistry;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.util.JsonUtil;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProjectQueryRepository {

    private static final Field<String> PROJECT_THEME_KEY =
        DSL.field(DSL.name("theme_key"), SQLDataType.VARCHAR(64));

    private final DSLContext dsl;
    private final ProjectAccessStrategyConfigRegistry accessStrategyConfigRegistry;
    private final ProjectDeploymentDriverConfigRegistry deploymentDriverConfigRegistry;
    private final ProjectReleaseArtifactSourceConfigRegistry releaseArtifactSourceConfigRegistry;
    private final ProjectRuntimeHealthProviderConfigRegistry runtimeHealthProviderConfigRegistry;

    public Optional<ProjectDefinition> getProject(String projectId) {
        Record row = dsl.select(
                PROJECTS.ID,
                PROJECTS.NAME,
                PROJECT_THEME_KEY,
                PROJECTS.RELEASE_INGEST_ENDPOINT_ID,
                PROJECTS.PROVIDER_CONNECTION_ID,
                PROJECTS.ACCESS_STRATEGY,
                PROJECTS.ACCESS_STRATEGY_CONFIG,
                PROJECTS.DEPLOYMENT_DRIVER,
                PROJECTS.DEPLOYMENT_DRIVER_CONFIG,
                PROJECTS.RELEASE_ARTIFACT_SOURCE,
                PROJECTS.RELEASE_ARTIFACT_SOURCE_CONFIG,
                PROJECTS.RUNTIME_HEALTH_PROVIDER,
                PROJECTS.RUNTIME_HEALTH_PROVIDER_CONFIG
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
                PROJECT_THEME_KEY,
                PROJECTS.RELEASE_INGEST_ENDPOINT_ID,
                PROJECTS.PROVIDER_CONNECTION_ID,
                PROJECTS.ACCESS_STRATEGY,
                PROJECTS.ACCESS_STRATEGY_CONFIG,
                PROJECTS.DEPLOYMENT_DRIVER,
                PROJECTS.DEPLOYMENT_DRIVER_CONFIG,
                PROJECTS.RELEASE_ARTIFACT_SOURCE,
                PROJECTS.RELEASE_ARTIFACT_SOURCE_CONFIG,
                PROJECTS.RUNTIME_HEALTH_PROVIDER,
                PROJECTS.RUNTIME_HEALTH_PROVIDER_CONFIG
            )
            .from(PROJECTS)
            .orderBy(PROJECTS.ID.asc())
            .fetch(this::toProjectDefinition);
    }

    private ProjectDefinition toProjectDefinition(Record row) {
        ProjectAccessStrategyType accessStrategyType = ProjectAccessStrategyType.valueOf(row.get(PROJECTS.ACCESS_STRATEGY).getLiteral());
        ProjectDeploymentDriverType deploymentDriverType = ProjectDeploymentDriverType.valueOf(
            row.get(PROJECTS.DEPLOYMENT_DRIVER).getLiteral()
        );
        ProjectReleaseArtifactSourceType releaseArtifactSourceType = ProjectReleaseArtifactSourceType.valueOf(
            row.get(PROJECTS.RELEASE_ARTIFACT_SOURCE).getLiteral()
        );
        ProjectRuntimeHealthProviderType runtimeHealthProviderType = ProjectRuntimeHealthProviderType.valueOf(
            row.get(PROJECTS.RUNTIME_HEALTH_PROVIDER).getLiteral()
        );
        return new ProjectDefinition(
            row.get(PROJECTS.ID),
            row.get(PROJECTS.NAME),
            row.get(PROJECT_THEME_KEY),
            normalize(row.get(PROJECTS.RELEASE_INGEST_ENDPOINT_ID)),
            normalize(row.get(PROJECTS.PROVIDER_CONNECTION_ID)),
            accessStrategyType,
            parseAccessStrategyConfig(accessStrategyType, row.get(PROJECTS.ACCESS_STRATEGY_CONFIG).data()),
            deploymentDriverType,
            parseDeploymentDriverConfig(deploymentDriverType, row.get(PROJECTS.DEPLOYMENT_DRIVER_CONFIG).data()),
            releaseArtifactSourceType,
            parseReleaseArtifactSourceConfig(releaseArtifactSourceType, row.get(PROJECTS.RELEASE_ARTIFACT_SOURCE_CONFIG).data()),
            runtimeHealthProviderType,
            parseRuntimeHealthProviderConfig(runtimeHealthProviderType, row.get(PROJECTS.RUNTIME_HEALTH_PROVIDER_CONFIG).data())
        );
    }

    private ProjectAccessStrategyConfig parseAccessStrategyConfig(ProjectAccessStrategyType type, String value) {
        return accessStrategyConfigRegistry.parse(type, value);
    }

    private ProjectDeploymentDriverConfig parseDeploymentDriverConfig(ProjectDeploymentDriverType type, String value) {
        return deploymentDriverConfigRegistry.parse(type, value);
    }

    private ProjectReleaseArtifactSourceConfig parseReleaseArtifactSourceConfig(
        ProjectReleaseArtifactSourceType type,
        String value
    ) {
        return releaseArtifactSourceConfigRegistry.parse(type, value);
    }

    private ProjectRuntimeHealthProviderConfig parseRuntimeHealthProviderConfig(
        ProjectRuntimeHealthProviderType type,
        String value
    ) {
        return runtimeHealthProviderConfigRegistry.parse(type, value);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
