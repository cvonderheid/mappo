package com.mappo.controlplane.integrations.azuredevops;

import com.mappo.controlplane.application.project.config.ProjectDeploymentDriverConfigDescriptor;
import com.mappo.controlplane.application.project.config.ProjectReleaseArtifactSourceConfigDescriptor;
import com.mappo.controlplane.application.project.config.StaticProjectDeploymentDriverConfigDescriptor;
import com.mappo.controlplane.application.project.config.StaticProjectReleaseArtifactSourceConfigDescriptor;
import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.PipelineTriggerDriverConfig;
import com.mappo.controlplane.model.RunPreviewMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureDevOpsProjectConfigDescriptorConfiguration {

    @Bean
    ProjectDeploymentDriverConfigDescriptor pipelineTriggerDriverConfigDescriptor() {
        return new StaticProjectDeploymentDriverConfigDescriptor(
            ProjectDeploymentDriverType.pipeline_trigger,
            PipelineTriggerDriverConfig.class,
            PipelineTriggerDriverConfig.defaults(),
            (config, hasDeploymentDriver, previewMode) -> pipelineCapabilities((PipelineTriggerDriverConfig) config)
        );
    }

    @Bean
    ProjectReleaseArtifactSourceConfigDescriptor externalDeploymentInputsArtifactSourceConfigDescriptor() {
        return new StaticProjectReleaseArtifactSourceConfigDescriptor(
            ProjectReleaseArtifactSourceType.external_deployment_inputs,
            ExternalDeploymentInputsArtifactSourceConfig.class,
            ExternalDeploymentInputsArtifactSourceConfig.defaults()
        );
    }

    private static DeploymentDriverCapabilities pipelineCapabilities(PipelineTriggerDriverConfig config) {
        return new DeploymentDriverCapabilities(
            false,
            RunPreviewMode.UNSUPPORTED,
            config.supportsExternalExecutionHandle(),
            config.supportsExternalLogs(),
            false
        );
    }
}
