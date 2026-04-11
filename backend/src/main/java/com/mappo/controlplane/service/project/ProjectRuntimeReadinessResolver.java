package com.mappo.controlplane.service.project;

import com.mappo.controlplane.application.project.ProjectRuntimeReadinessProvider;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectRuntimeReadinessResolver {

    private final List<ProjectRuntimeReadinessProvider> providers;

    public boolean isRuntimeConfigured(ProjectDefinition project) {
        List<ProjectRuntimeReadinessProvider> matchingProviders = providers.stream()
            .filter(provider -> provider.supports(project))
            .toList();
        if (matchingProviders.isEmpty()) {
            return true;
        }
        return matchingProviders.stream().allMatch(provider -> provider.isConfigured(project));
    }
}
