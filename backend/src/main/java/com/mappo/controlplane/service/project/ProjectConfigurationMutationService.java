package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProjectConfigurationPatchRequest;
import com.mappo.controlplane.api.request.ProjectCreateRequest;
import com.mappo.controlplane.application.project.config.ProjectAccessStrategyConfigRegistry;
import com.mappo.controlplane.application.project.config.ProjectDeploymentDriverConfigRegistry;
import com.mappo.controlplane.application.project.config.ProjectReleaseArtifactSourceConfigRegistry;
import com.mappo.controlplane.application.project.config.ProjectRuntimeHealthProviderConfigRegistry;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.util.JsonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectConfigurationMutationService {

    private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,126}[a-z0-9])?$");
    private static final Set<String> ALLOWED_THEME_KEYS = Set.of("harbor-teal", "vectr-signal", "scalr-slate");

    private final JsonUtil jsonUtil;
    private final ProjectAccessStrategyConfigRegistry accessStrategyConfigRegistry;
    private final ProjectDeploymentDriverConfigRegistry deploymentDriverConfigRegistry;
    private final ProjectReleaseArtifactSourceConfigRegistry releaseArtifactSourceConfigRegistry;
    private final ProjectRuntimeHealthProviderConfigRegistry runtimeHealthProviderConfigRegistry;

    public ProjectConfigurationMutationRecord fromCreate(String projectId, ProjectCreateRequest request) {
        String id = requiredProjectId(projectId);
        String name = requiredName(request.name());
        String themeKey = optionalThemeKey(request.themeKey());
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
            themeKey,
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
        String themeKey = patchRequest.themeKey() == null
            ? optionalThemeKey(current.themeKey())
            : optionalThemeKey(patchRequest.themeKey());
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
            themeKey,
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
        snapshot.put("themeKey", optionalThemeKey(mutation.themeKey()));
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
            optionalThemeKey(project.themeKey()),
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
        accessStrategyConfigRegistry.validate(type, config, "accessStrategyConfig");
    }

    private void validateTypedConfig(ProjectDeploymentDriverType type, Map<String, Object> config) {
        deploymentDriverConfigRegistry.validate(type, config, "deploymentDriverConfig");
    }

    private void validateTypedConfig(ProjectReleaseArtifactSourceType type, Map<String, Object> config) {
        releaseArtifactSourceConfigRegistry.validate(type, config, "releaseArtifactSourceConfig");
    }

    private void validateTypedConfig(ProjectRuntimeHealthProviderType type, Map<String, Object> config) {
        runtimeHealthProviderConfigRegistry.validate(type, config, "runtimeHealthProviderConfig");
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
        return accessStrategyConfigRegistry.defaultsAsMap(type);
    }

    private Map<String, Object> defaultDriverConfig(ProjectDeploymentDriverType type) {
        return deploymentDriverConfigRegistry.defaultsAsMap(type);
    }

    private Map<String, Object> defaultReleaseSourceConfig(ProjectReleaseArtifactSourceType type) {
        return releaseArtifactSourceConfigRegistry.defaultsAsMap(type);
    }

    private Map<String, Object> defaultRuntimeHealthConfig(ProjectRuntimeHealthProviderType type) {
        return runtimeHealthProviderConfigRegistry.defaultsAsMap(type);
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

    private String optionalThemeKey(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }
        if (!ALLOWED_THEME_KEYS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid themeKey");
        }
        return normalized;
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
