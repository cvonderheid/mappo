package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ProjectConfigurationPatchRequest;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.repository.ProjectCommandRepository;
import com.mappo.controlplane.util.JsonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectConfigurationCommandService {

    private final ProjectCatalogService projectCatalogService;
    private final ProjectCommandRepository projectCommandRepository;
    private final JsonUtil jsonUtil;

    @Transactional
    public ProjectDefinition patchProjectConfiguration(String projectId, ProjectConfigurationPatchRequest patchRequest) {
        ProjectDefinition current = projectCatalogService.getRequired(projectId);
        if (patchRequest == null) {
            return current;
        }

        String name = firstNonBlank(patchRequest.name(), current.name());
        if (name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project name must not be blank");
        }

        Map<String, Object> accessStrategyConfig = merge(
            jsonUtil.toMap(current.accessStrategyConfig()),
            patchRequest.accessStrategyConfig()
        );
        Map<String, Object> deploymentDriverConfig = merge(
            jsonUtil.toMap(current.deploymentDriverConfig()),
            patchRequest.deploymentDriverConfig()
        );
        Map<String, Object> releaseArtifactSourceConfig = merge(
            jsonUtil.toMap(current.releaseArtifactSourceConfig()),
            patchRequest.releaseArtifactSourceConfig()
        );
        Map<String, Object> runtimeHealthProviderConfig = merge(
            jsonUtil.toMap(current.runtimeHealthProviderConfig()),
            patchRequest.runtimeHealthProviderConfig()
        );

        projectCommandRepository.updateProjectConfiguration(
            current.id(),
            name,
            accessStrategyConfig,
            deploymentDriverConfig,
            releaseArtifactSourceConfig,
            runtimeHealthProviderConfig
        );
        return projectCatalogService.getRequired(current.id());
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
