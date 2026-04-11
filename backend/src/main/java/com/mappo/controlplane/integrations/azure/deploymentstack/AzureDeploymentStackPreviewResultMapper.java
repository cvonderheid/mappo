package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.azure.core.management.exception.ManagementError;
import com.azure.resourcemanager.resources.models.ChangeType;
import com.azure.resourcemanager.resources.models.WhatIfChange;
import com.azure.resourcemanager.resources.models.WhatIfPropertyChange;
import com.mappo.controlplane.integrations.azure.AzureFailureDiagnostics;
import com.mappo.controlplane.model.RunPreviewChangeRecord;
import com.mappo.controlplane.model.RunPreviewPropertyChangeRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.domain.execution.TargetPreviewException;
import com.mappo.controlplane.domain.execution.TargetPreviewOutcome;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AzureDeploymentStackPreviewResultMapper {

    public TargetPreviewOutcome toPreviewOutcome(List<WhatIfChange> changes) {
        List<RunPreviewChangeRecord> records = toChangeRecords(changes);
        return new TargetPreviewOutcome(
            summarizeChanges(records),
            previewWarnings(records),
            records
        );
    }

    public TargetPreviewException previewFailure(
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
            collectPropertyChanges(records, seen, propertyChange, "");
        }
        return records;
    }

    private void collectPropertyChanges(
        List<RunPreviewPropertyChangeRecord> records,
        LinkedHashSet<String> seen,
        WhatIfPropertyChange propertyChange,
        String parentPath
    ) {
        if (propertyChange == null) {
            return;
        }
        String qualifiedPath = qualifyPropertyPath(parentPath, propertyChange.path());
        String key = qualifiedPath + "|" + normalize(propertyChange.propertyChangeType());
        if (!qualifiedPath.isBlank() && seen.add(key)) {
            records.add(new RunPreviewPropertyChangeRecord(
                qualifiedPath,
                normalize(propertyChange.propertyChangeType())
            ));
        }
        if (propertyChange.children() == null) {
            return;
        }
        for (WhatIfPropertyChange child : propertyChange.children()) {
            collectPropertyChanges(records, seen, child, qualifiedPath);
        }
    }

    private String qualifyPropertyPath(String parentPath, String path) {
        String normalizedParent = normalize(parentPath);
        String normalizedPath = normalize(path);
        if (normalizedPath.isBlank()) {
            return normalizedParent;
        }
        if (normalizedPath.startsWith("$")) {
            return normalizedPath;
        }
        if (normalizedParent.isBlank()) {
            return normalizedPath;
        }
        if (normalizedPath.startsWith("[")) {
            return normalizedParent + normalizedPath;
        }
        if (normalizedPath.matches("\\d+")) {
            return normalizedParent + "[" + normalizedPath + "]";
        }
        if (normalizedPath.startsWith(".")) {
            return normalizedParent + normalizedPath;
        }
        return normalizedParent + "." + normalizedPath;
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
                parts.add(count + " " + changeType);
            }
        }
        if (parts.isEmpty()) {
            return "ARM what-if found resource changes.";
        }
        return "ARM what-if found " + total + " resource changes (" + String.join(", ", parts) + ").";
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
