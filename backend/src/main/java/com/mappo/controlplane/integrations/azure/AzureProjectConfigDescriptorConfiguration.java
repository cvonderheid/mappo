package com.mappo.controlplane.integrations.azure;

import com.mappo.controlplane.application.project.config.ProjectAccessStrategyConfigDescriptor;
import com.mappo.controlplane.application.project.config.ProjectDeploymentDriverConfigDescriptor;
import com.mappo.controlplane.application.project.config.ProjectReleaseArtifactSourceConfigDescriptor;
import com.mappo.controlplane.application.project.config.ProjectRuntimeHealthProviderConfigDescriptor;
import com.mappo.controlplane.application.project.config.StaticProjectAccessStrategyConfigDescriptor;
import com.mappo.controlplane.application.project.config.StaticProjectDeploymentDriverConfigDescriptor;
import com.mappo.controlplane.application.project.config.StaticProjectReleaseArtifactSourceConfigDescriptor;
import com.mappo.controlplane.application.project.config.StaticProjectRuntimeHealthProviderConfigDescriptor;
import com.mappo.controlplane.domain.execution.DeploymentDriverCapabilities;
import com.mappo.controlplane.domain.project.ProjectAccessStrategyType;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.integrations.azure.access.config.AzureWorkloadRbacAccessStrategyConfig;
import com.mappo.controlplane.integrations.azure.access.config.LighthouseDelegatedAccessStrategyConfig;
import com.mappo.controlplane.integrations.azure.deploymentstack.config.AzureDeploymentStackDriverConfig;
import com.mappo.controlplane.integrations.azure.deploymentstack.config.BlobArmTemplateArtifactSourceConfig;
import com.mappo.controlplane.integrations.azure.runtime.config.AzureContainerAppHttpRuntimeHealthProviderConfig;
import com.mappo.controlplane.integrations.azure.templatespec.config.AzureTemplateSpecDriverConfig;
import com.mappo.controlplane.integrations.azure.templatespec.config.TemplateSpecResourceArtifactSourceConfig;
import com.mappo.controlplane.model.RunPreviewMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureProjectConfigDescriptorConfiguration {

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
    ProjectRuntimeHealthProviderConfigDescriptor azureContainerAppHttpRuntimeHealthProviderConfigDescriptor() {
        return new StaticProjectRuntimeHealthProviderConfigDescriptor(
            ProjectRuntimeHealthProviderType.azure_container_app_http,
            AzureContainerAppHttpRuntimeHealthProviderConfig.class,
            AzureContainerAppHttpRuntimeHealthProviderConfig.defaults()
        );
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
}
