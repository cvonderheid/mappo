package com.mappo.controlplane.domain.project;

public sealed interface ProjectDeploymentDriverConfig
    permits AzureDeploymentStackDriverConfig, AzureTemplateSpecDriverConfig, PipelineTriggerDriverConfig {
}
