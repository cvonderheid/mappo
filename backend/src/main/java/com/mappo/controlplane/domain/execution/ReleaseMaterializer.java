package com.mappo.controlplane.domain.execution;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;

public interface ReleaseMaterializer<T> {

    boolean supports(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured);

    Class<T> materializedType();

    T materialize(ProjectDefinition project, ReleaseRecord release, TargetExecutionContextRecord target);
}
