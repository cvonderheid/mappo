package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROJECTS;

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
import org.jooq.JSONB;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProjectCommandRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public void createProject(ProjectConfigurationMutationRecord mutation) {
        dsl.insertInto(PROJECTS)
            .set(PROJECTS.ID, normalize(mutation.id()))
            .set(PROJECTS.NAME, normalize(mutation.name()))
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

    private JSONB jsonb(Map<String, Object> value) {
        return JSONB.valueOf(jsonUtil.write(value));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
