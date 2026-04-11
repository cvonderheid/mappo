package com.mappo.controlplane.application.project;

import com.mappo.controlplane.domain.project.ProjectDefinition;

public interface ProjectRuntimeReadinessProvider {

    boolean supports(ProjectDefinition project);

    boolean isConfigured(ProjectDefinition project);
}
