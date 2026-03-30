package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProjectConfigurationPatchRequest;
import com.mappo.controlplane.api.request.ProjectCreateRequest;
import com.mappo.controlplane.domain.project.AzureContainerAppHttpRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.AzureDeploymentStackDriverConfig;
import com.mappo.controlplane.domain.project.AzureTemplateSpecDriverConfig;
import com.mappo.controlplane.domain.project.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.domain.project.BlobArmTemplateArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.domain.project.HttpEndpointRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.LighthouseDelegatedAccessStrategyConfig;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.domain.project.SimulatorAccessStrategyConfig;
import com.mappo.controlplane.domain.project.TemplateSpecResourceArtifactSourceConfig;
import com.mappo.controlplane.util.JsonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProjectConfigurationMutationService {

    private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,126}[a-z0-9])?$");

    private final JsonUtil jsonUtil;
    private final ObjectMapper objectMapper;

    public ProjectConfigurationMutationRecord fromCreate(ProjectCreateRequest request) {
        String id = requiredProjectId(request.id());
        String name = requiredName(request.name());
        String releaseIngestEndpointId = optionalIdentifier(request.releaseIngestEndpointId());
        String providerConnectionId = optionalIdentifier(request.providerConnectionId());
        ProjectAccessStrategyType accessStrategy = required(request.accessStrategy(), "accessStrategy");
        ProjectDeploymentDriverType deploymentDriver = required(request.deploymentDriver(), "deploymentDriver");
        ProjectReleaseArtifactSourceType releaseArtifactSource = required(request.releaseArtifactSource(), "releaseArtifactSource");
        ProjectRuntimeHealthProviderType runtimeHealthProvider = required(request.runtimeHealthProvider(), "runtimeHealthProvider");

        Map<String, Object> accessConfig = merge(defaultAccessConfig(accessStrategy), request.accessStrategyConfig());
        Map<String, Object> driverConfig = merge(defaultDriverConfig(deploymentDriver), request.deploymentDriverConfig());
        Map<String, Object> sourceConfig = merge(defaultReleaseSourceConfig(releaseArtifactSource), request.releaseArtifactSourceConfig());
        Map<String, Object> runtimeConfig = merge(defaultRuntimeHealthConfig(runtimeHealthProvider), request.runtimeHealthProviderConfig());

        validateTypedConfig(accessStrategy, accessConfig);
        validateTypedConfig(deploymentDriver, driverConfig);
        validateTypedConfig(releaseArtifactSource, sourceConfig);
        validateTypedConfig(runtimeHealthProvider, runtimeConfig);
        validateDriverSourceCompatibility(deploymentDriver, releaseArtifactSource);

        return new ProjectConfigurationMutationRecord(
            id,
            name,
            releaseIngestEndpointId,
            providerConnectionId,
            accessStrategy,
            accessConfig,
            deploymentDriver,
            driverConfig,
            releaseArtifactSource,
            sourceConfig,
            runtimeHealthProvider,
            runtimeConfig
        );
    }

    public ProjectConfigurationMutationRecord fromPatch(ProjectDefinition current, ProjectConfigurationPatchRequest patchRequest) {
        if (patchRequest == null) {
            return toMutation(current);
        }

        String id = requiredProjectId(current.id());
        String name = firstNonBlank(patchRequest.name(), current.name());
        if (name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project name must not be blank");
        }
        String releaseIngestEndpointId = patchRequest.releaseIngestEndpointId() == null
            ? optionalIdentifier(current.releaseIngestEndpointId())
            : optionalIdentifier(patchRequest.releaseIngestEndpointId());
        String providerConnectionId = patchRequest.providerConnectionId() == null
            ? optionalIdentifier(current.providerConnectionId())
            : optionalIdentifier(patchRequest.providerConnectionId());

        ProjectAccessStrategyType accessStrategy = patchRequest.accessStrategy() == null
            ? current.accessStrategy()
            : patchRequest.accessStrategy();
        ProjectDeploymentDriverType deploymentDriver = patchRequest.deploymentDriver() == null
            ? current.deploymentDriver()
            : patchRequest.deploymentDriver();
        ProjectReleaseArtifactSourceType releaseArtifactSource = patchRequest.releaseArtifactSource() == null
            ? current.releaseArtifactSource()
            : patchRequest.releaseArtifactSource();
        ProjectRuntimeHealthProviderType runtimeHealthProvider = patchRequest.runtimeHealthProvider() == null
            ? current.runtimeHealthProvider()
            : patchRequest.runtimeHealthProvider();

        Map<String, Object> accessConfig = merge(
            patchRequest.accessStrategy() == null
                ? jsonUtil.toMap(current.accessStrategyConfig())
                : defaultAccessConfig(accessStrategy),
            patchRequest.accessStrategyConfig()
        );
        Map<String, Object> driverConfig = merge(
            patchRequest.deploymentDriver() == null
                ? jsonUtil.toMap(current.deploymentDriverConfig())
                : defaultDriverConfig(deploymentDriver),
            patchRequest.deploymentDriverConfig()
        );
        Map<String, Object> sourceConfig = merge(
            patchRequest.releaseArtifactSource() == null
                ? jsonUtil.toMap(current.releaseArtifactSourceConfig())
                : defaultReleaseSourceConfig(releaseArtifactSource),
            patchRequest.releaseArtifactSourceConfig()
        );
        Map<String, Object> runtimeConfig = merge(
            patchRequest.runtimeHealthProvider() == null
                ? jsonUtil.toMap(current.runtimeHealthProviderConfig())
                : defaultRuntimeHealthConfig(runtimeHealthProvider),
            patchRequest.runtimeHealthProviderConfig()
        );

        validateTypedConfig(accessStrategy, accessConfig);
        validateTypedConfig(deploymentDriver, driverConfig);
        validateTypedConfig(releaseArtifactSource, sourceConfig);
        validateTypedConfig(runtimeHealthProvider, runtimeConfig);
        validateDriverSourceCompatibility(deploymentDriver, releaseArtifactSource);

        return new ProjectConfigurationMutationRecord(
            id,
            name,
            releaseIngestEndpointId,
            providerConnectionId,
            accessStrategy,
            accessConfig,
            deploymentDriver,
            driverConfig,
            releaseArtifactSource,
            sourceConfig,
            runtimeHealthProvider,
            runtimeConfig
        );
    }

    public Map<String, Object> snapshot(ProjectDefinition project) {
        return snapshot(toMutation(project));
    }

    public Map<String, Object> snapshot(ProjectConfigurationMutationRecord mutation) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", normalize(mutation.id()));
        snapshot.put("name", normalize(mutation.name()));
        snapshot.put("releaseIngestEndpointId", optionalIdentifier(mutation.releaseIngestEndpointId()));
        snapshot.put("providerConnectionId", optionalIdentifier(mutation.providerConnectionId()));
        snapshot.put("accessStrategy", mutation.accessStrategy());
        snapshot.put("accessStrategyConfig", mutation.accessStrategyConfig() == null ? Map.of() : mutation.accessStrategyConfig());
        snapshot.put("deploymentDriver", mutation.deploymentDriver());
        snapshot.put("deploymentDriverConfig", mutation.deploymentDriverConfig() == null ? Map.of() : mutation.deploymentDriverConfig());
        snapshot.put("releaseArtifactSource", mutation.releaseArtifactSource());
        snapshot.put("releaseArtifactSourceConfig", mutation.releaseArtifactSourceConfig() == null ? Map.of() : mutation.releaseArtifactSourceConfig());
        snapshot.put("runtimeHealthProvider", mutation.runtimeHealthProvider());
        snapshot.put("runtimeHealthProviderConfig", mutation.runtimeHealthProviderConfig() == null ? Map.of() : mutation.runtimeHealthProviderConfig());
        return snapshot;
    }

    private ProjectConfigurationMutationRecord toMutation(ProjectDefinition project) {
        return new ProjectConfigurationMutationRecord(
            requiredProjectId(project.id()),
            requiredName(project.name()),
            optionalIdentifier(project.releaseIngestEndpointId()),
            optionalIdentifier(project.providerConnectionId()),
            project.accessStrategy(),
            jsonUtil.toMap(project.accessStrategyConfig()),
            project.deploymentDriver(),
            jsonUtil.toMap(project.deploymentDriverConfig()),
            project.releaseArtifactSource(),
            jsonUtil.toMap(project.releaseArtifactSourceConfig()),
            project.runtimeHealthProvider(),
            jsonUtil.toMap(project.runtimeHealthProviderConfig())
        );
    }

    private void validateTypedConfig(ProjectAccessStrategyType type, Map<String, Object> config) {
        switch (type) {
            case simulator -> convert(config, SimulatorAccessStrategyConfig.class, "accessStrategyConfig");
            case azure_workload_rbac -> convert(config, AzureWorkloadRbacAccessStrategyConfig.class, "accessStrategyConfig");
            case lighthouse_delegated_access -> convert(config, LighthouseDelegatedAccessStrategyConfig.class, "accessStrategyConfig");
        }
    }

    private void validateTypedConfig(ProjectDeploymentDriverType type, Map<String, Object> config) {
        switch (type) {
            case azure_deployment_stack -> convert(config, AzureDeploymentStackDriverConfig.class, "deploymentDriverConfig");
            case azure_template_spec -> convert(config, AzureTemplateSpecDriverConfig.class, "deploymentDriverConfig");
            case pipeline_trigger -> convert(config, PipelineTriggerDriverConfig.class, "deploymentDriverConfig");
        }
    }

    private void validateTypedConfig(ProjectReleaseArtifactSourceType type, Map<String, Object> config) {
        switch (type) {
            case blob_arm_template -> convert(config, BlobArmTemplateArtifactSourceConfig.class, "releaseArtifactSourceConfig");
            case template_spec_resource ->
                convert(config, TemplateSpecResourceArtifactSourceConfig.class, "releaseArtifactSourceConfig");
            case external_deployment_inputs ->
                convert(config, ExternalDeploymentInputsArtifactSourceConfig.class, "releaseArtifactSourceConfig");
        }
    }

    private void validateTypedConfig(ProjectRuntimeHealthProviderType type, Map<String, Object> config) {
        switch (type) {
            case azure_container_app_http ->
                convert(config, AzureContainerAppHttpRuntimeHealthProviderConfig.class, "runtimeHealthProviderConfig");
            case http_endpoint -> convert(config, HttpEndpointRuntimeHealthProviderConfig.class, "runtimeHealthProviderConfig");
        }
    }

    private void validateDriverSourceCompatibility(
        ProjectDeploymentDriverType deploymentDriver,
        ProjectReleaseArtifactSourceType releaseArtifactSource
    ) {
        boolean compatible = switch (deploymentDriver) {
            case azure_deployment_stack -> releaseArtifactSource == ProjectReleaseArtifactSourceType.blob_arm_template;
            case azure_template_spec -> releaseArtifactSource == ProjectReleaseArtifactSourceType.template_spec_resource;
            case pipeline_trigger -> releaseArtifactSource == ProjectReleaseArtifactSourceType.external_deployment_inputs;
        };
        if (!compatible) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "deploymentDriver " + deploymentDriver + " is not compatible with releaseArtifactSource " + releaseArtifactSource
            );
        }
    }

    private <T> T convert(Map<String, Object> config, Class<T> type, String fieldName) {
        try {
            return objectMapper.convertValue(config == null ? Map.of() : config, type);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid " + fieldName + " for " + type.getSimpleName() + ": " + normalize(exception.getMessage())
            );
        }
    }

    private Map<String, Object> merge(Map<String, Object> base, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(base == null ? Map.of() : base);
        if (patch == null || patch.isEmpty()) {
            return merged;
        }
        patch.forEach((rawKey, value) -> {
            String key = normalize(rawKey);
            if (key.isBlank()) {
                return;
            }
            if (value == null) {
                merged.remove(key);
                return;
            }
            merged.put(key, value);
        });
        return merged;
    }

    private Map<String, Object> defaultAccessConfig(ProjectAccessStrategyType type) {
        return switch (type) {
            case simulator -> jsonUtil.toMap(SimulatorAccessStrategyConfig.defaults());
            case azure_workload_rbac -> jsonUtil.toMap(AzureWorkloadRbacAccessStrategyConfig.defaults());
            case lighthouse_delegated_access -> jsonUtil.toMap(LighthouseDelegatedAccessStrategyConfig.defaults());
        };
    }

    private Map<String, Object> defaultDriverConfig(ProjectDeploymentDriverType type) {
        return switch (type) {
            case azure_deployment_stack -> jsonUtil.toMap(AzureDeploymentStackDriverConfig.defaults());
            case azure_template_spec -> jsonUtil.toMap(AzureTemplateSpecDriverConfig.defaults());
            case pipeline_trigger -> jsonUtil.toMap(PipelineTriggerDriverConfig.defaults());
        };
    }

    private Map<String, Object> defaultReleaseSourceConfig(ProjectReleaseArtifactSourceType type) {
        return switch (type) {
            case blob_arm_template -> jsonUtil.toMap(BlobArmTemplateArtifactSourceConfig.defaults());
            case template_spec_resource -> jsonUtil.toMap(TemplateSpecResourceArtifactSourceConfig.defaults());
            case external_deployment_inputs -> jsonUtil.toMap(ExternalDeploymentInputsArtifactSourceConfig.defaults());
        };
    }

    private Map<String, Object> defaultRuntimeHealthConfig(ProjectRuntimeHealthProviderType type) {
        return switch (type) {
            case azure_container_app_http -> jsonUtil.toMap(AzureContainerAppHttpRuntimeHealthProviderConfig.defaults());
            case http_endpoint -> jsonUtil.toMap(HttpEndpointRuntimeHealthProviderConfig.defaults());
        };
    }

    private String requiredProjectId(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project id must not be blank");
        }
        if (!PROJECT_ID_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "project id must match " + PROJECT_ID_PATTERN.pattern()
            );
        }
        return normalized;
    }

    private String requiredName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project name must not be blank");
        }
        return normalized;
    }

    private String optionalIdentifier(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
