package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROJECTS;

import com.mappo.controlplane.domain.project.AzureContainerAppHttpRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.AzureDeploymentStackDriverConfig;
import com.mappo.controlplane.domain.project.AzureTemplateSpecDriverConfig;
import com.mappo.controlplane.domain.project.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.domain.project.BlobArmTemplateArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.domain.project.HttpEndpointRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.LighthouseDelegatedAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.domain.project.SimulatorAccessStrategyConfig;
import com.mappo.controlplane.domain.project.TemplateSpecResourceArtifactSourceConfig;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import com.mappo.controlplane.util.JsonUtil;

@Repository
@RequiredArgsConstructor
public class ProjectQueryRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public Optional<ProjectDefinition> getProject(String projectId) {
        Record row = dsl.select(
                PROJECTS.ID,
                PROJECTS.NAME,
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
            row.get(PROJECTS.RELEASE_INGEST_ENDPOINT_ID),
            row.get(PROJECTS.PROVIDER_CONNECTION_ID),
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
        return switch (type) {
            case simulator -> jsonUtil.read(value, SimulatorAccessStrategyConfig.class, SimulatorAccessStrategyConfig.defaults());
            case azure_workload_rbac ->
                jsonUtil.read(value, AzureWorkloadRbacAccessStrategyConfig.class, AzureWorkloadRbacAccessStrategyConfig.defaults());
            case lighthouse_delegated_access ->
                jsonUtil.read(
                    value,
                    LighthouseDelegatedAccessStrategyConfig.class,
                    LighthouseDelegatedAccessStrategyConfig.defaults()
                );
        };
    }

    private ProjectDeploymentDriverConfig parseDeploymentDriverConfig(ProjectDeploymentDriverType type, String value) {
        return switch (type) {
            case azure_deployment_stack ->
                jsonUtil.read(value, AzureDeploymentStackDriverConfig.class, AzureDeploymentStackDriverConfig.defaults());
            case azure_template_spec ->
                jsonUtil.read(value, AzureTemplateSpecDriverConfig.class, AzureTemplateSpecDriverConfig.defaults());
            case pipeline_trigger ->
                jsonUtil.read(value, PipelineTriggerDriverConfig.class, PipelineTriggerDriverConfig.defaults());
        };
    }

    private ProjectReleaseArtifactSourceConfig parseReleaseArtifactSourceConfig(
        ProjectReleaseArtifactSourceType type,
        String value
    ) {
        return switch (type) {
            case blob_arm_template ->
                jsonUtil.read(value, BlobArmTemplateArtifactSourceConfig.class, BlobArmTemplateArtifactSourceConfig.defaults());
            case template_spec_resource ->
                jsonUtil.read(
                    value,
                    TemplateSpecResourceArtifactSourceConfig.class,
                    TemplateSpecResourceArtifactSourceConfig.defaults()
                );
            case external_deployment_inputs ->
                jsonUtil.read(
                    value,
                    ExternalDeploymentInputsArtifactSourceConfig.class,
                    ExternalDeploymentInputsArtifactSourceConfig.defaults()
                );
        };
    }

    private ProjectRuntimeHealthProviderConfig parseRuntimeHealthProviderConfig(
        ProjectRuntimeHealthProviderType type,
        String value
    ) {
        return switch (type) {
            case azure_container_app_http ->
                jsonUtil.read(
                    value,
                    AzureContainerAppHttpRuntimeHealthProviderConfig.class,
                    AzureContainerAppHttpRuntimeHealthProviderConfig.defaults()
                );
            case http_endpoint ->
                jsonUtil.read(value, HttpEndpointRuntimeHealthProviderConfig.class, HttpEndpointRuntimeHealthProviderConfig.defaults());
        };
    }
}
