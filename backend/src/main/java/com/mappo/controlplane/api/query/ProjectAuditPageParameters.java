package com.mappo.controlplane.api.query;

import com.mappo.controlplane.model.ProjectConfigurationAuditAction;
import com.mappo.controlplane.model.query.ProjectConfigurationAuditPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectAuditPageParameters extends PageQueryParameters {

    @Schema(description = "Optional action filter for project audit events.")
    private ProjectConfigurationAuditAction action;

    public ProjectConfigurationAuditPageQuery toQuery(String projectId) {
        return new ProjectConfigurationAuditPageQuery(getPage(), getSize(), projectId, action);
    }
}

