package com.mappo.controlplane.infrastructure.azure.deploymentstack;

import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.azure.resourcemanager.resources.models.DeploymentParameter;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.execution.ReleaseMaterializer;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DeploymentStackReleaseMaterializer implements ReleaseMaterializer<DeploymentStackTemplateInputs> {

    private final AzureExecutorClient azureExecutorClient;
    private final MappoProperties properties;
    private final ReleaseArtifactTemplateLoader releaseArtifactTemplateLoader;

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean azureConfigured) {
        return project.deploymentDriver() == ProjectDeploymentDriverType.azure_deployment_stack
            && project.releaseArtifactSource() == ProjectReleaseArtifactSourceType.blob_arm_template;
    }

    @Override
    public Class<DeploymentStackTemplateInputs> materializedType() {
        return DeploymentStackTemplateInputs.class;
    }

    @Override
    public DeploymentStackTemplateInputs materialize(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        String tenantId = uuidText(target.tenantId(), "tenantId");
        String subscriptionId = uuidText(target.subscriptionId(), "subscriptionId");
        ContainerAppsApiManager containerAppsManager = azureExecutorClient.createContainerAppsManager(tenantId, subscriptionId);
        ContainerApp currentContainerApp = containerAppsManager.containerApps().getById(target.containerAppResourceId());
        return new DeploymentStackTemplateInputs(
            normalize(target.managedResourceGroupId()),
            releaseArtifactTemplateLoader.loadTemplateDefinition(release),
            deploymentParameters(release.parameterDefaults(), target, currentContainerApp)
        );
    }

    private Map<String, DeploymentParameter> deploymentParameters(
        Map<String, String> defaults,
        TargetExecutionContextRecord target,
        ContainerApp currentContainerApp
    ) {
        Map<String, DeploymentParameter> parameters = new LinkedHashMap<>();
        mergeParameterValues(parameters, defaults);
        mergeParameterValues(parameters, targetParameterDefaults(defaults, target, currentContainerApp));
        return parameters;
    }

    private void mergeParameterValues(Map<String, DeploymentParameter> targetParameters, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = normalize(entry.getKey());
            if (key.isBlank()) {
                continue;
            }
            targetParameters.put(key, new DeploymentParameter().withValue(entry.getValue()));
        }
    }

    private Map<String, String> targetParameterDefaults(
        Map<String, String> defaults,
        TargetExecutionContextRecord target,
        ContainerApp currentContainerApp
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("containerAppName", normalize(currentContainerApp.name()));
        values.put(
            "managedEnvironmentResourceId",
            firstNonBlank(currentContainerApp.managedEnvironmentId(), currentContainerApp.environmentId())
        );
        values.put("location", normalize(currentContainerApp.regionName()));
        values.put("targetGroup", firstNonBlank(target.tags().get("ring"), "prod"));
        values.put("tenantId", uuidText(target.tenantId(), "tenantId"));
        values.put("targetId", normalize(target.targetId()));
        values.put("tier", firstNonBlank(target.tags().get("tier"), "standard"));
        values.put("environment", firstNonBlank(target.tags().get("environment"), "prod"));
        mergeStringValues(values, registryParameters(defaults, target));
        return values;
    }

    private void mergeStringValues(Map<String, String> targetValues, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        targetValues.putAll(values);
    }

    private Map<String, String> registryParameters(Map<String, String> defaults, TargetExecutionContextRecord target) {
        String containerImage = defaults == null ? "" : normalize(defaults.get("containerImage"));
        MappoRegistryAuthMode authMode = target.registryAuthMode() == null
            ? defaultRegistryAuthMode(containerImage)
            : target.registryAuthMode();

        if (authMode == null || authMode == MappoRegistryAuthMode.none) {
            if (imageRequiresRegistryAuth(containerImage)) {
                throw new IllegalArgumentException(
                    "Target " + target.targetId() + " requires registry auth for image " + containerImage + "."
                );
            }
            return Map.of();
        }

        if (authMode == MappoRegistryAuthMode.customer_managed_secret) {
            throw new IllegalArgumentException(
                "registry_auth_mode customer_managed_secret is not implemented yet for deployment_stack execution"
            );
        }

        String registryServer = firstNonBlank(
            target.registryServer(),
            defaults == null ? null : defaults.get("registryServer"),
            deriveRegistryServer(containerImage),
            properties.getPublisherAcr().getServer()
        );
        String registryUsername = firstNonBlank(
            target.registryUsername(),
            defaults == null ? null : defaults.get("registryUsername"),
            properties.getPublisherAcr().getPullClientId()
        );
        String registryPasswordSecretName = firstNonBlank(
            target.registryPasswordSecretName(),
            defaults == null ? null : defaults.get("registryPasswordSecretName"),
            properties.getPublisherAcr().getPullSecretName()
        );
        String registryPassword = normalize(properties.getPublisherAcr().getPullClientSecret());

        if (registryServer.isBlank() || registryUsername.isBlank() || registryPassword.isBlank()) {
            throw new IllegalArgumentException(
                "publisher ACR pull credentials are incomplete for deployment_stack execution"
            );
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("registryServer", registryServer);
        values.put("registryUsername", registryUsername);
        values.put("registryPasswordSecretName", registryPasswordSecretName);
        values.put("registryPassword", registryPassword);
        return values;
    }

    private boolean imageRequiresRegistryAuth(String containerImage) {
        String registryServer = deriveRegistryServer(containerImage);
        if (registryServer.isBlank()) {
            return false;
        }
        return registryServer.endsWith(".azurecr.io");
    }

    private MappoRegistryAuthMode defaultRegistryAuthMode(String containerImage) {
        return imageRequiresRegistryAuth(containerImage)
            ? MappoRegistryAuthMode.shared_service_principal_secret
            : MappoRegistryAuthMode.none;
    }

    private String deriveRegistryServer(String containerImage) {
        String normalized = normalize(containerImage);
        int slashIndex = normalized.indexOf('/');
        if (slashIndex <= 0) {
            return "";
        }
        String candidate = normalized.substring(0, slashIndex);
        return candidate.contains(".") ? candidate : "";
    }

    private String uuidText(Object value, String fieldName) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for deployment_stack execution");
        }
        return text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
