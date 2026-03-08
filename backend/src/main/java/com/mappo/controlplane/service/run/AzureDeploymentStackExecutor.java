package com.mappo.controlplane.service.run;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.ContainerApp;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.DeploymentStackInner;
import com.azure.resourcemanager.resources.models.ActionOnUnmanage;
import com.azure.resourcemanager.resources.models.DeploymentParameter;
import com.azure.resourcemanager.resources.models.DeploymentStacksDeleteDetachEnum;
import com.azure.resourcemanager.resources.models.DenySettings;
import com.azure.resourcemanager.resources.models.DenySettingsMode;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackExecutor implements DeploymentStackExecutor {

    private final AzureExecutorClient azureExecutorClient;
    private final MappoProperties properties;
    private final ReleaseArtifactTemplateLoader releaseArtifactTemplateLoader;

    @Override
    public TargetDeploymentOutcome deploy(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        String tenantId = uuidText(target.tenantId(), "tenantId");
        String subscriptionId = uuidText(target.subscriptionId(), "subscriptionId");
        String stackName = resolveStackName(target);
        String deploymentScope = normalize(target.managedResourceGroupId());
        String stackResourceGroupName = resourceGroupNameFromResourceId(deploymentScope);

        try {
            ContainerAppsApiManager containerAppsManager = azureExecutorClient.createContainerAppsManager(tenantId, subscriptionId);
            ContainerApp currentContainerApp = containerAppsManager.containerApps().getById(target.containerAppResourceId());
            ResourceManager resourceManager = azureExecutorClient.createResourceManager(tenantId, subscriptionId);

            DeploymentStackInner deploymentStack = buildDeploymentStack(stackName, release, target, currentContainerApp, deploymentScope);
            DeploymentStackInner result = resourceManager.deploymentStackClient()
                .getDeploymentStacks()
                .createOrUpdateAtResourceGroup(stackResourceGroupName, stackName, deploymentStack);

            String correlationId = fallbackCorrelationId(normalize(result.correlationId()), runId, target.targetId());
            ManagementError error = result.error();
            if (error != null) {
                throw deploymentFailure(
                    "Azure deployment stack completed with an error state.",
                    error,
                    correlationId,
                    stackName,
                    normalize(result.id()),
                    normalize(result.deploymentId())
                );
            }

            String provisioningState = normalize(result.provisioningState());
            if (!"succeeded".equalsIgnoreCase(provisioningState)) {
                throw new TargetDeploymentException(
                    "Azure deployment stack finished with provisioning state " + provisioningState + ".",
                    new StageErrorRecord(
                        "AZURE_DEPLOYMENT_STACK_NOT_SUCCEEDED",
                        "Azure deployment stack finished with provisioning state " + provisioningState + ".",
                        new StageErrorDetailsRecord(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            correlationId,
                            stackName,
                            normalize(result.deploymentId()),
                            normalize(result.id())
                        )
                    ),
                    correlationId,
                    ""
                );
            }

            return new TargetDeploymentOutcome(
                correlationId,
                "Deployment Stack " + stackName + " succeeded.",
                ""
            );
        } catch (ManagementException error) {
            ManagementError managementError = error.getValue();
            String correlationId = fallbackCorrelationId(
                responseHeader(error, "x-ms-correlation-request-id"),
                runId,
                target.targetId()
            );
            throw deploymentFailure(
                "Azure deployment stack request failed.",
                managementError,
                correlationId,
                stackName,
                deploymentScope,
                ""
            );
        } catch (IllegalArgumentException error) {
            throw new TargetDeploymentException(
                error.getMessage(),
                new StageErrorRecord(
                    "AZURE_DEPLOYMENT_STACK_CONFIGURATION_INVALID",
                    error.getMessage(),
                    new StageErrorDetailsRecord(
                        null,
                        error.getMessage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        stackName,
                        null,
                        deploymentScope
                    )
                ),
                fallbackCorrelationId(null, runId, target.targetId()),
                ""
            );
        }
    }

    private DeploymentStackInner buildDeploymentStack(
        String stackName,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        ContainerApp currentContainerApp,
        String deploymentScope
    ) {
        return new DeploymentStackInner()
            .withDescription("MAPPO deployment stack for target " + normalize(target.targetId()))
            .withDeploymentScope(deploymentScope)
            .withTemplate(releaseArtifactTemplateLoader.loadTemplateDefinition(release))
            .withParameters(deploymentParameters(release.parameterDefaults(), target, currentContainerApp))
            .withDenySettings(defaultDenySettings())
            .withActionOnUnmanage(defaultActionOnUnmanage())
            .withBypassStackOutOfSyncError(Boolean.TRUE);
    }

    private ActionOnUnmanage defaultActionOnUnmanage() {
        return new ActionOnUnmanage()
            .withResources(DeploymentStacksDeleteDetachEnum.DETACH)
            .withResourceGroups(DeploymentStacksDeleteDetachEnum.DETACH);
    }

    private DenySettings defaultDenySettings() {
        return new DenySettings().withMode(DenySettingsMode.NONE);
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
            properties.getPublisherAcrServer()
        );
        String registryUsername = firstNonBlank(
            target.registryUsername(),
            defaults == null ? null : defaults.get("registryUsername"),
            properties.getPublisherAcrPullClientId()
        );
        String registryPasswordSecretName = firstNonBlank(
            target.registryPasswordSecretName(),
            defaults == null ? null : defaults.get("registryPasswordSecretName"),
            properties.getPublisherAcrPullSecretName()
        );
        String registryPassword = normalize(properties.getPublisherAcrPullClientSecret());

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

    private String resourceGroupNameFromResourceId(String resourceId) {
        String normalized = normalize(resourceId);
        String marker = "/resourceGroups/";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("managedResourceGroupId must be a valid Azure resource group resource ID");
        }
        String remaining = normalized.substring(markerIndex + marker.length());
        int nextSlash = remaining.indexOf('/');
        return nextSlash < 0 ? remaining : remaining.substring(0, nextSlash);
    }

    private TargetDeploymentException deploymentFailure(
        String prefix,
        ManagementError error,
        String correlationId,
        String stackName,
        String resourceId,
        String operationId
    ) {
        String azureCode = error == null ? null : normalize(error.getCode());
        String azureMessage = error == null ? null : normalize(error.getMessage());
        String message = !azureMessage.isBlank()
            ? azureMessage
            : prefix;

        return new TargetDeploymentException(
            message,
            new StageErrorRecord(
                "AZURE_DEPLOYMENT_STACK_FAILED",
                message,
                new StageErrorDetailsRecord(
                    null,
                    error == null ? prefix : error.toString(),
                    null,
                    nullable(azureCode),
                    nullable(azureMessage),
                    null,
                    null,
                    nullable(correlationId),
                    nullable(stackName),
                    nullable(operationId),
                    nullable(resourceId)
                )
            ),
            correlationId,
            ""
        );
    }

    private String resolveStackName(TargetExecutionContextRecord target) {
        String configured = normalize(target.deploymentStackName());
        return configured.isBlank() ? buildStackName(target.targetId()) : configured;
    }

    private String buildStackName(String targetId) {
        String suffix = sanitize(targetId);
        if (suffix.length() > 48) {
            suffix = suffix.substring(0, 48);
        }
        return "mappo-stack-" + suffix;
    }

    private String sanitize(String value) {
        String text = normalize(value).toLowerCase();
        String sanitized = text.replaceAll("[^a-z0-9-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "target" : sanitized;
    }

    private String fallbackCorrelationId(String value, String runId, String targetId) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "corr-" + sanitize(runId + "-" + targetId + "-stack");
    }

    private String responseHeader(ManagementException error, String name) {
        if (error.getResponse() == null || error.getResponse().getHeaders() == null) {
            return "";
        }
        return normalize(error.getResponse().getHeaders().getValue(name));
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

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
