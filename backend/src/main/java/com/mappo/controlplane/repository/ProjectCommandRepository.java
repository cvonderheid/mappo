package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.MARKETPLACE_EVENTS;
import static com.mappo.controlplane.jooq.Tables.PROJECTS;
import static com.mappo.controlplane.jooq.Tables.RELEASES;
import static com.mappo.controlplane.jooq.Tables.RUNS;
import static com.mappo.controlplane.jooq.Tables.TARGETS;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.jooq.enums.MappoProjectAccessStrategy;
import com.mappo.controlplane.jooq.enums.MappoProjectDeploymentDriver;
import com.mappo.controlplane.jooq.enums.MappoProjectReleaseArtifactSource;
import com.mappo.controlplane.jooq.enums.MappoProjectRuntimeHealthProvider;
import com.mappo.controlplane.service.project.ProjectConfigurationMutationRecord;
import com.mappo.controlplane.util.JsonUtil;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProjectCommandRepository {

    private static final Field<String> MARKETPLACE_EVENT_PROJECT_ID =
        DSL.field(DSL.name("project_id"), SQLDataType.VARCHAR(128));

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public void createProject(ProjectConfigurationMutationRecord mutation) {
        dsl.insertInto(PROJECTS)
            .set(PROJECTS.ID, normalize(mutation.id()))
            .set(PROJECTS.NAME, normalize(mutation.name()))
            .set(PROJECTS.RELEASE_INGEST_ENDPOINT_ID, optionalIdentifier(mutation.releaseIngestEndpointId()))
            .set(PROJECTS.PROVIDER_CONNECTION_ID, optionalIdentifier(mutation.providerConnectionId()))
            .set(PROJECTS.ACCESS_STRATEGY, requiredAccessStrategy(mutation))
            .set(PROJECTS.ACCESS_STRATEGY_CONFIG, jsonb(mutation.accessStrategyConfig()))
            .set(PROJECTS.DEPLOYMENT_DRIVER, requiredDeploymentDriver(mutation))
            .set(PROJECTS.DEPLOYMENT_DRIVER_CONFIG, jsonb(mutation.deploymentDriverConfig()))
            .set(PROJECTS.RELEASE_ARTIFACT_SOURCE, requiredReleaseArtifactSource(mutation))
            .set(PROJECTS.RELEASE_ARTIFACT_SOURCE_CONFIG, jsonb(mutation.releaseArtifactSourceConfig()))
            .set(PROJECTS.RUNTIME_HEALTH_PROVIDER, requiredRuntimeHealthProvider(mutation))
            .set(PROJECTS.RUNTIME_HEALTH_PROVIDER_CONFIG, jsonb(mutation.runtimeHealthProviderConfig()))
            .set(PROJECTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute();
    }

    public void updateProjectConfiguration(
        ProjectConfigurationMutationRecord mutation
    ) {
        int updated = dsl.update(PROJECTS)
            .set(PROJECTS.NAME, normalize(mutation.name()))
            .set(PROJECTS.RELEASE_INGEST_ENDPOINT_ID, optionalIdentifier(mutation.releaseIngestEndpointId()))
            .set(PROJECTS.PROVIDER_CONNECTION_ID, optionalIdentifier(mutation.providerConnectionId()))
            .set(PROJECTS.ACCESS_STRATEGY, requiredAccessStrategy(mutation))
            .set(PROJECTS.ACCESS_STRATEGY_CONFIG, jsonb(mutation.accessStrategyConfig()))
            .set(PROJECTS.DEPLOYMENT_DRIVER, requiredDeploymentDriver(mutation))
            .set(PROJECTS.DEPLOYMENT_DRIVER_CONFIG, jsonb(mutation.deploymentDriverConfig()))
            .set(PROJECTS.RELEASE_ARTIFACT_SOURCE, requiredReleaseArtifactSource(mutation))
            .set(PROJECTS.RELEASE_ARTIFACT_SOURCE_CONFIG, jsonb(mutation.releaseArtifactSourceConfig()))
            .set(PROJECTS.RUNTIME_HEALTH_PROVIDER, requiredRuntimeHealthProvider(mutation))
            .set(PROJECTS.RUNTIME_HEALTH_PROVIDER_CONFIG, jsonb(mutation.runtimeHealthProviderConfig()))
            .set(PROJECTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PROJECTS.ID.eq(normalize(mutation.id())))
            .execute();
        if (updated <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project not found: " + normalize(mutation.id()));
        }
    }

    public void deleteProjectCascade(String projectId) {
        String normalizedProjectId = normalize(projectId);
        dsl.deleteFrom(MARKETPLACE_EVENTS)
            .where(MARKETPLACE_EVENT_PROJECT_ID.eq(normalizedProjectId))
            .execute();
        dsl.deleteFrom(RUNS)
            .where(RUNS.PROJECT_ID.eq(normalizedProjectId))
            .execute();
        dsl.deleteFrom(TARGETS)
            .where(TARGETS.PROJECT_ID.eq(normalizedProjectId))
            .execute();
        dsl.deleteFrom(RELEASES)
            .where(RELEASES.PROJECT_ID.eq(normalizedProjectId))
            .execute();
        int deleted = dsl.deleteFrom(PROJECTS)
            .where(PROJECTS.ID.eq(normalizedProjectId))
            .execute();
        if (deleted <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project not found: " + normalizedProjectId);
        }
    }

    private JSONB jsonb(Map<String, Object> value) {
        return JSONB.valueOf(jsonUtil.write(value));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String optionalIdentifier(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private MappoProjectAccessStrategy requiredAccessStrategy(ProjectConfigurationMutationRecord mutation) {
        MappoProjectAccessStrategy value = MappoProjectAccessStrategy.lookupLiteral(normalize(mutation.accessStrategy()));
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid accessStrategy");
        }
        return value;
    }

    private MappoProjectDeploymentDriver requiredDeploymentDriver(ProjectConfigurationMutationRecord mutation) {
        MappoProjectDeploymentDriver value = MappoProjectDeploymentDriver.lookupLiteral(normalize(mutation.deploymentDriver()));
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid deploymentDriver");
        }
        return value;
    }

    private MappoProjectReleaseArtifactSource requiredReleaseArtifactSource(ProjectConfigurationMutationRecord mutation) {
        MappoProjectReleaseArtifactSource value = MappoProjectReleaseArtifactSource.lookupLiteral(
            normalize(mutation.releaseArtifactSource())
        );
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid releaseArtifactSource");
        }
        return value;
    }

    private MappoProjectRuntimeHealthProvider requiredRuntimeHealthProvider(ProjectConfigurationMutationRecord mutation) {
        MappoProjectRuntimeHealthProvider value = MappoProjectRuntimeHealthProvider.lookupLiteral(
            normalize(mutation.runtimeHealthProvider())
        );
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid runtimeHealthProvider");
        }
        return value;
    }
}
