package com.mappo.controlplane.service.run;

import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.WhatIfOperationResultInner;
import com.azure.resourcemanager.resources.models.ChangeType;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.azure.resourcemanager.resources.models.DeploymentWhatIf;
import com.azure.resourcemanager.resources.models.DeploymentWhatIfProperties;
import com.azure.resourcemanager.resources.models.DeploymentWhatIfSettings;
import com.azure.resourcemanager.resources.models.WhatIfChange;
import com.azure.resourcemanager.resources.models.WhatIfPropertyChange;
import com.azure.resourcemanager.resources.models.WhatIfResultFormat;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunPreviewChangeRecord;
import com.mappo.controlplane.model.RunPreviewPropertyChangeRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureDeploymentStackPreviewExecutor implements DeploymentStackPreviewExecutor {

    private final AzureExecutorClient azureExecutorClient;
    private final DeploymentStackTemplateInputsFactory templateInputsFactory;

    @Override
    public TargetPreviewOutcome preview(ReleaseRecord release, TargetExecutionContextRecord target) {
        String tenantId = uuidText(target.tenantId(), "tenantId");
        String subscriptionId = uuidText(target.subscriptionId(), "subscriptionId");
        DeploymentStackTemplateInputs inputs = templateInputsFactory.resolve(release, target);
        String resourceGroupName = resourceGroupNameFromResourceId(inputs.deploymentScope());
        String deploymentName = buildPreviewDeploymentName(target.targetId());
        try {
            ResourceManager resourceManager = azureExecutorClient.createResourceManager(tenantId, subscriptionId);
            WhatIfOperationResultInner result = resourceManager.serviceClient()
                .getDeployments()
                .whatIf(resourceGroupName, deploymentName, whatIfRequest(inputs));

            if (result.error() != null) {
                throw previewFailure(
                    "ARM what-if preview failed.",
                    result.error(),
                    null,
                    null,
                    null,
                    deploymentName,
                    inputs.deploymentScope()
                );
            }

            List<RunPreviewChangeRecord> changes = toChangeRecords(result.changes());
            return new TargetPreviewOutcome(
                summarizeChanges(changes),
                previewWarnings(changes),
                changes
            );
        } catch (ManagementException error) {
            throw previewFailure(
                "ARM what-if preview request failed.",
                error.getValue(),
                error.getResponse() == null ? null : error.getResponse().getStatusCode(),
                responseHeader(error, "x-ms-request-id"),
                responseHeader(error, "x-ms-arm-service-request-id"),
                deploymentName,
                inputs.deploymentScope()
            );
        }
    }

    private DeploymentWhatIf whatIfRequest(DeploymentStackTemplateInputs inputs) {
        return new DeploymentWhatIf()
            .withProperties(
                new DeploymentWhatIfProperties()
                    .withMode(DeploymentMode.INCREMENTAL)
                    .withTemplate(inputs.template())
                    .withParameters(inputs.parameters())
                    .withWhatIfSettings(
                        new DeploymentWhatIfSettings().withResultFormat(WhatIfResultFormat.FULL_RESOURCE_PAYLOADS)
                    )
            );
    }

    private List<RunPreviewChangeRecord> toChangeRecords(List<WhatIfChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        List<RunPreviewChangeRecord> records = new ArrayList<>();
        for (WhatIfChange change : changes) {
            if (change == null) {
                continue;
            }
            records.add(new RunPreviewChangeRecord(
                normalize(change.resourceId()),
                normalize(change.changeType()),
                nullable(change.unsupportedReason()),
                flattenPropertyChanges(change.delta())
            ));
        }
        return records;
    }

    private List<RunPreviewPropertyChangeRecord> flattenPropertyChanges(List<WhatIfPropertyChange> delta) {
        if (delta == null || delta.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RunPreviewPropertyChangeRecord> records = new ArrayList<>();
        for (WhatIfPropertyChange propertyChange : delta) {
            collectPropertyChanges(records, seen, propertyChange);
        }
        return records;
    }

    private void collectPropertyChanges(
        List<RunPreviewPropertyChangeRecord> records,
        LinkedHashSet<String> seen,
        WhatIfPropertyChange propertyChange
    ) {
        if (propertyChange == null) {
            return;
        }
        String key = normalize(propertyChange.path()) + "|" + normalize(propertyChange.propertyChangeType());
        if (!normalize(propertyChange.path()).isBlank() && seen.add(key)) {
            records.add(new RunPreviewPropertyChangeRecord(
                normalize(propertyChange.path()),
                normalize(propertyChange.propertyChangeType())
            ));
        }
        if (propertyChange.children() == null) {
            return;
        }
        for (WhatIfPropertyChange child : propertyChange.children()) {
            collectPropertyChanges(records, seen, child);
        }
    }

    private List<String> previewWarnings(List<RunPreviewChangeRecord> changes) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        for (RunPreviewChangeRecord change : changes) {
            if (change.unsupportedReason() != null && !change.unsupportedReason().isBlank()) {
                warnings.add(
                    "Azure what-if marked " + firstNonBlank(change.resourceId(), "a resource")
                        + " as unsupported: " + change.unsupportedReason()
                );
            }
        }
        return List.copyOf(warnings);
    }

    private String summarizeChanges(List<RunPreviewChangeRecord> changes) {
        if (changes.isEmpty()) {
            return "ARM what-if found no resource changes.";
        }

        Map<ChangeType, Integer> counts = new EnumMap<>(ChangeType.class);
        int total = 0;
        for (RunPreviewChangeRecord change : changes) {
            ChangeType changeType = ChangeType.fromString(change.changeType());
            if (changeType == null) {
                continue;
            }
            counts.merge(changeType, 1, Integer::sum);
            total += 1;
        }

        List<String> parts = new ArrayList<>();
        for (ChangeType changeType : ChangeType.values()) {
            Integer count = counts.get(changeType);
            if (count != null && count > 0) {
                parts.add(count + " " + changeType.toString());
            }
        }
        if (parts.isEmpty()) {
            return "ARM what-if found resource changes.";
        }
        return "ARM what-if found " + total + " resource changes (" + String.join(", ", parts) + ").";
    }

    private TargetPreviewException previewFailure(
        String prefix,
        ManagementError error,
        Integer statusCode,
        String requestId,
        String armServiceRequestId,
        String deploymentName,
        String resourceId
    ) {
        AzureFailureDiagnostics.AzureFailureSnapshot snapshot = AzureFailureDiagnostics.summarize(
            prefix,
            error,
            statusCode,
            requestId,
            armServiceRequestId,
            null,
            deploymentName,
            null,
            resourceId,
            null,
            null
        );
        return new TargetPreviewException(
            snapshot.message(),
            new StageErrorRecord(
                "AZURE_ARM_WHAT_IF_FAILED",
                snapshot.message(),
                snapshot.details()
            )
        );
    }

    private String buildPreviewDeploymentName(String targetId) {
        return "mappo-preview-" + sanitize(targetId);
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

    private String responseHeader(ManagementException error, String name) {
        if (error.getResponse() == null || error.getResponse().getHeaders() == null) {
            return "";
        }
        return normalize(error.getResponse().getHeaders().getValue(name));
    }

    private String sanitize(String value) {
        String text = normalize(value).toLowerCase();
        String sanitized = text.replaceAll("[^a-z0-9-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "target" : sanitized;
    }

    private String uuidText(Object value, String fieldName) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for deployment_stack preview");
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
