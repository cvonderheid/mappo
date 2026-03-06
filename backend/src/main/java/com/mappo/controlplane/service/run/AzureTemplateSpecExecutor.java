package com.mappo.controlplane.service.run;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureTemplateSpecExecutor implements TemplateSpecExecutor {

    private static final String DEPLOYMENT_TEMPLATE_SCHEMA =
        "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#";
    private static final String DEPLOYMENT_RESOURCE_API_VERSION = "2022-09-01";

    private final AzureExecutorClient azureExecutorClient;

    @Override
    public TargetDeploymentOutcome deploy(
        String runId,
        ReleaseRecord release,
        TargetExecutionContextRecord target
    ) {
        String templateSpecVersionId = resolveTemplateSpecVersionId(release);
        String resourceGroupName = parseResourceGroupName(target.managedResourceGroupId(), target.targetId());
        String deploymentName = buildDeploymentName(runId, target.targetId());

        try {
            Deployment deployment = azureExecutorClient.createManager(
                    uuidText(target.tenantId(), "tenantId"),
                    uuidText(target.subscriptionId(), "subscriptionId")
                )
                .deployments()
                .define(deploymentName)
                .withExistingResourceGroup(resourceGroupName)
                .withTemplate(wrapperTemplate(templateSpecVersionId, release))
                .withParameters(Map.of())
                .withMode(toAzureMode(release.executionSettings().armMode()))
                .create();

            String correlationId = fallbackCorrelationId(deployment.correlationId(), runId, target.targetId());
            ManagementError error = deployment.error();
            if (error != null) {
                throw deploymentFailure(
                    "Azure deployment completed with an error state.",
                    error,
                    correlationId,
                    deploymentName,
                    target.containerAppResourceId()
                );
            }

            String provisioningState = normalize(deployment.provisioningState());
            if (!"succeeded".equalsIgnoreCase(provisioningState)) {
                throw new TargetDeploymentException(
                    "Azure deployment finished with provisioning state " + provisioningState + ".",
                    new StageErrorRecord(
                        "AZURE_DEPLOYMENT_NOT_SUCCEEDED",
                        "Azure deployment finished with provisioning state " + provisioningState + ".",
                        new StageErrorDetailsRecord(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            correlationId,
                            deploymentName,
                            null,
                            target.containerAppResourceId()
                        )
                    ),
                    correlationId,
                    ""
                );
            }

            return new TargetDeploymentOutcome(
                correlationId,
                "Template Spec deployment " + deploymentName + " succeeded.",
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
                "Azure deployment request failed.",
                managementError,
                correlationId,
                deploymentName,
                target.containerAppResourceId()
            );
        } catch (IllegalArgumentException error) {
            throw new TargetDeploymentException(
                error.getMessage(),
                new StageErrorRecord(
                    "AZURE_DEPLOYMENT_CONFIGURATION_INVALID",
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
                        deploymentName,
                        null,
                        target.containerAppResourceId()
                    )
                ),
                fallbackCorrelationId(null, runId, target.targetId()),
                ""
            );
        }
    }

    private Map<String, Object> wrapperTemplate(String templateSpecVersionId, ReleaseRecord release) {
        Map<String, Object> resourceProperties = new LinkedHashMap<>();
        resourceProperties.put("mode", toAzureMode(release.executionSettings().armMode()).toString());
        resourceProperties.put("templateLink", Map.of("id", templateSpecVersionId));
        resourceProperties.put("parameters", deploymentParameters(release.parameterDefaults()));

        Map<String, Object> nestedDeployment = new LinkedHashMap<>();
        nestedDeployment.put("type", "Microsoft.Resources/deployments");
        nestedDeployment.put("apiVersion", DEPLOYMENT_RESOURCE_API_VERSION);
        nestedDeployment.put("name", "mappo-linked-template-spec");
        nestedDeployment.put("properties", resourceProperties);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("$schema", DEPLOYMENT_TEMPLATE_SCHEMA);
        template.put("contentVersion", "1.0.0.0");
        template.put("resources", new Object[]{nestedDeployment});
        return template;
    }

    private Map<String, Object> deploymentParameters(Map<String, String> defaults) {
        if (defaults == null || defaults.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            String key = normalize(entry.getKey());
            if (key.isBlank()) {
                continue;
            }
            parameters.put(key, Map.of("value", entry.getValue()));
        }
        return parameters;
    }

    private TargetDeploymentException deploymentFailure(
        String prefix,
        ManagementError error,
        String correlationId,
        String deploymentName,
        String resourceId
    ) {
        String azureCode = error == null ? null : normalize(error.getCode());
        String azureMessage = error == null ? null : normalize(error.getMessage());
        String message = !azureMessage.isBlank()
            ? azureMessage
            : prefix;

        return new TargetDeploymentException(
            message,
            new StageErrorRecord(
                "AZURE_DEPLOYMENT_FAILED",
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
                    nullable(deploymentName),
                    null,
                    nullable(resourceId)
                )
            ),
            correlationId,
            ""
        );
    }

    private String resolveTemplateSpecVersionId(ReleaseRecord release) {
        String versionRef = normalize(release.sourceVersionRef());
        if (!versionRef.isBlank()) {
            return versionRef;
        }

        String sourceRef = normalize(release.sourceRef());
        if (sourceRef.contains("/versions/")) {
            return sourceRef;
        }
        if (sourceRef.isBlank()) {
            throw new IllegalArgumentException("release sourceRef is required for template_spec execution");
        }

        String version = normalize(release.sourceVersion());
        if (version.isBlank()) {
            throw new IllegalArgumentException("release sourceVersion is required to resolve the Template Spec version ID");
        }
        return sourceRef + "/versions/" + version;
    }

    private String parseResourceGroupName(String managedResourceGroupId, String targetId) {
        String resourceId = normalize(managedResourceGroupId);
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("target " + targetId + " is missing managedResourceGroupId");
        }
        String resourceGroupName = ResourceId.fromString(resourceId).resourceGroupName();
        if (normalize(resourceGroupName).isBlank()) {
            throw new IllegalArgumentException("target " + targetId + " has an invalid managedResourceGroupId");
        }
        return resourceGroupName;
    }

    private DeploymentMode toAzureMode(MappoArmDeploymentMode mode) {
        if (mode == MappoArmDeploymentMode.complete) {
            return DeploymentMode.COMPLETE;
        }
        return DeploymentMode.INCREMENTAL;
    }

    private String buildDeploymentName(String runId, String targetId) {
        String suffix = sanitize(runId + "-" + targetId);
        if (suffix.length() > 54) {
            suffix = suffix.substring(0, 54);
        }
        return "mappo-" + suffix;
    }

    private String sanitize(String value) {
        String text = normalize(value).toLowerCase();
        String sanitized = text.replaceAll("[^a-z0-9-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "deployment" : sanitized;
    }

    private String fallbackCorrelationId(String value, String runId, String targetId) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "corr-" + sanitize(runId + "-" + targetId);
    }

    private String responseHeader(ManagementException error, String name) {
        if (error.getResponse() == null || error.getResponse().getHeaders() == null) {
            return "";
        }
        return normalize(error.getResponse().getHeaders().getValue(name));
    }

    private String uuidText(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.toString();
    }

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
