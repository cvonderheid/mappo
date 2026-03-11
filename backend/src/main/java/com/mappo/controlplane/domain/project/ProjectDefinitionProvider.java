package com.mappo.controlplane.domain.project;

import com.mappo.controlplane.model.ReleaseRecord;

public interface ProjectDefinitionProvider {

    boolean supports(ReleaseRecord release);

    ProjectDefinition definition(ReleaseRecord release);
}
