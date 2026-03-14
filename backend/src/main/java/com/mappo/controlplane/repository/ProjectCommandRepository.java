package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROJECTS;

import com.mappo.controlplane.api.ApiException;
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

    public void updateProjectConfiguration(
        String projectId,
        String name,
        Map<String, Object> accessStrategyConfig,
        Map<String, Object> deploymentDriverConfig,
        Map<String, Object> releaseArtifactSourceConfig,
        Map<String, Object> runtimeHealthProviderConfig
    ) {
        int updated = dsl.update(PROJECTS)
            .set(PROJECTS.NAME, normalize(name))
            .set(PROJECTS.ACCESS_STRATEGY_CONFIG, jsonb(accessStrategyConfig))
            .set(PROJECTS.DEPLOYMENT_DRIVER_CONFIG, jsonb(deploymentDriverConfig))
            .set(PROJECTS.RELEASE_ARTIFACT_SOURCE_CONFIG, jsonb(releaseArtifactSourceConfig))
            .set(PROJECTS.RUNTIME_HEALTH_PROVIDER_CONFIG, jsonb(runtimeHealthProviderConfig))
            .set(PROJECTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PROJECTS.ID.eq(normalize(projectId)))
            .execute();
        if (updated <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project not found: " + normalize(projectId));
        }
    }

    private JSONB jsonb(Map<String, Object> value) {
        return JSONB.valueOf(jsonUtil.write(value));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
