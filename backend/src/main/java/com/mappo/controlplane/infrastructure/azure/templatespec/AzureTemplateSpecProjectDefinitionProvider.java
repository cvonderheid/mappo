package com.mappo.controlplane.infrastructure.azure.templatespec;

import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDefinitionProvider;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import org.springframework.stereotype.Component;

@Component
public class AzureTemplateSpecProjectDefinitionProvider implements ProjectDefinitionProvider {

    @Override
    public boolean supports(ReleaseRecord release) {
        return release.sourceType() == MappoReleaseSourceType.template_spec;
    }

    @Override
    public ProjectDefinition definition(ReleaseRecord release) {
        return new ProjectDefinition(
            "azure-managed-app-template-spec",
            "Azure Managed App Template Spec",
            ProjectAccessStrategyType.azure_workload_rbac,
            ProjectDeploymentDriverType.azure_template_spec,
            ProjectReleaseArtifactSourceType.template_spec_resource,
            ProjectRuntimeHealthProviderType.azure_container_app_http
        );
    }
}
