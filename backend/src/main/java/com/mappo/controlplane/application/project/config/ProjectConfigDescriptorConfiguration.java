package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.domain.project.AzureContainerAppHttpRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.AzureDeploymentStackDriverConfig;
import com.mappo.controlplane.domain.project.AzureTemplateSpecDriverConfig;
import com.mappo.controlplane.domain.project.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.domain.project.BlobArmTemplateArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ExternalDeploymentInputsArtifactSourceConfig;
import com.mappo.controlplane.domain.project.HttpEndpointRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.LighthouseDelegatedAccessStrategyConfig;
import com.mappo.controlplane.domain.project.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyConfig;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceConfig;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.domain.project.SimulatorAccessStrategyConfig;
import com.mappo.controlplane.domain.project.TemplateSpecResourceArtifactSourceConfig;
import com.mappo.controlplane.model.RunPreviewMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectConfigDescriptorConfiguration {

    @Bean
    ProjectAccessStrategyConfigDescriptor simulatorAccessStrategyConfigDescriptor() {
        return new StaticProjectAccessStrategyConfigDescriptor(
            ProjectAccessStrategyType.simulator,
            SimulatorAccessStrategyConfig.class,
            SimulatorAccessStrategyConfig.defaults()
        );
    }

    @Bean
    ProjectAccessStrategyConfigDescriptor azureWorkloadRbacAccessStrategyConfigDescriptor() {
        return new StaticProjectAccessStrategyConfigDescriptor(
            ProjectAccessStrategyType.azure_workload_rbac,
            AzureWorkloadRbacAccessStrategyConfig.class,
            AzureWorkloadRbacAccessStrategyConfig.defaults()
        );
    }

    @Bean
    ProjectAccessStrategyConfigDescriptor lighthouseDelegatedAccessStrategyConfigDescriptor() {
        return new StaticProjectAccessStrategyConfigDescriptor(
            ProjectAccessStrategyType.lighthouse_delegated_access,
            LighthouseDelegatedAccessStrategyConfig.class,
            LighthouseDelegatedAccessStrategyConfig.defaults()
        );
    }

    @Bean
    ProjectDeploymentDriverConfigDescriptor azureDeploymentStackDriverConfigDescriptor() {
        return new StaticProjectDeploymentDriverConfigDescriptor(
            ProjectDeploymentDriverType.azure_deployment_stack,
            AzureDeploymentStackDriverConfig.class,
            AzureDeploymentStackDriverConfig.defaults(),
            (config, hasDeploymentDriver, previewMode) -> previewCapableCapabilities(
                (AzureDeploymentStackDriverConfig) config,
                hasDeploymentDriver,
                previewMode
            )
        );
    }

    @Bean
    ProjectDeploymentDriverConfigDescriptor azureTemplateSpecDriverConfigDescriptor() {
        return new StaticProjectDeploymentDriverConfigDescriptor(
            ProjectDeploymentDriverType.azure_template_spec,
            AzureTemplateSpecDriverConfig.class,
            AzureTemplateSpecDriverConfig.defaults(),
            (config, hasDeploymentDriver, previewMode) -> previewCapableCapabilities(
                (AzureTemplateSpecDriverConfig) config,
                hasDeploymentDriver,
                previewMode
            )
        );
    }

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
    ProjectReleaseArtifactSourceConfigDescriptor blobArmTemplateArtifactSourceConfigDescriptor() {
        return new StaticProjectReleaseArtifactSourceConfigDescriptor(
            ProjectReleaseArtifactSourceType.blob_arm_template,
            BlobArmTemplateArtifactSourceConfig.class,
            BlobArmTemplateArtifactSourceConfig.defaults()
        );
    }

    @Bean
    ProjectReleaseArtifactSourceConfigDescriptor templateSpecResourceArtifactSourceConfigDescriptor() {
        return new StaticProjectReleaseArtifactSourceConfigDescriptor(
            ProjectReleaseArtifactSourceType.template_spec_resource,
            TemplateSpecResourceArtifactSourceConfig.class,
            TemplateSpecResourceArtifactSourceConfig.defaults()
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

    @Bean
    ProjectRuntimeHealthProviderConfigDescriptor azureContainerAppHttpRuntimeHealthProviderConfigDescriptor() {
        return new StaticProjectRuntimeHealthProviderConfigDescriptor(
            ProjectRuntimeHealthProviderType.azure_container_app_http,
            AzureContainerAppHttpRuntimeHealthProviderConfig.class,
            AzureContainerAppHttpRuntimeHealthProviderConfig.defaults()
        );
    }

    @Bean
    ProjectRuntimeHealthProviderConfigDescriptor httpEndpointRuntimeHealthProviderConfigDescriptor() {
        return new StaticProjectRuntimeHealthProviderConfigDescriptor(
            ProjectRuntimeHealthProviderType.http_endpoint,
            HttpEndpointRuntimeHealthProviderConfig.class,
            HttpEndpointRuntimeHealthProviderConfig.defaults()
        );
    }

    private record StaticProjectAccessStrategyConfigDescriptor(
        ProjectAccessStrategyType key,
        Class<? extends ProjectAccessStrategyConfig> configType,
        ProjectAccessStrategyConfig defaults
    ) implements ProjectAccessStrategyConfigDescriptor {
    }

    private record StaticProjectDeploymentDriverConfigDescriptor(
        ProjectDeploymentDriverType key,
        Class<? extends ProjectDeploymentDriverConfig> configType,
        ProjectDeploymentDriverConfig defaults,
        DriverCapabilitiesResolver capabilitiesResolver
    ) implements ProjectDeploymentDriverConfigDescriptor {

        @Override
        public DeploymentDriverCapabilities capabilities(
            ProjectDeploymentDriverConfig config,
            boolean hasDeploymentDriver,
            RunPreviewMode previewMode
        ) {
            return capabilitiesResolver.resolve(config, hasDeploymentDriver, previewMode);
        }
    }

    private record StaticProjectReleaseArtifactSourceConfigDescriptor(
        ProjectReleaseArtifactSourceType key,
        Class<? extends ProjectReleaseArtifactSourceConfig> configType,
        ProjectReleaseArtifactSourceConfig defaults
    ) implements ProjectReleaseArtifactSourceConfigDescriptor {
    }

    private record StaticProjectRuntimeHealthProviderConfigDescriptor(
        ProjectRuntimeHealthProviderType key,
        Class<? extends ProjectRuntimeHealthProviderConfig> configType,
        ProjectRuntimeHealthProviderConfig defaults
    ) implements ProjectRuntimeHealthProviderConfigDescriptor {
    }

    private static DeploymentDriverCapabilities previewCapableCapabilities(
        AzureDeploymentStackDriverConfig config,
        boolean hasDeploymentDriver,
        RunPreviewMode previewMode
    ) {
        return new DeploymentDriverCapabilities(
            hasDeploymentDriver && config.supportsPreview(),
            config.supportsPreview() ? previewMode : RunPreviewMode.UNSUPPORTED,
            config.supportsExternalExecutionHandle(),
            false,
            false
        );
    }

    private static DeploymentDriverCapabilities previewCapableCapabilities(
        AzureTemplateSpecDriverConfig config,
        boolean hasDeploymentDriver,
        RunPreviewMode previewMode
    ) {
        return new DeploymentDriverCapabilities(
            hasDeploymentDriver && config.supportsPreview(),
            config.supportsPreview() ? previewMode : RunPreviewMode.UNSUPPORTED,
            config.supportsExternalExecutionHandle(),
            false,
            false
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

    @FunctionalInterface
    private interface DriverCapabilitiesResolver {
        DeploymentDriverCapabilities resolve(
            ProjectDeploymentDriverConfig config,
            boolean hasDeploymentDriver,
            RunPreviewMode previewMode
        );
    }
}
