package com.mappo.controlplane.infrastructure.azure;

import com.azure.core.management.exception.ManagementError;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.models.DeploymentOperationProperties;
import com.azure.resourcemanager.resources.models.ResourceReferenceExtended;
import com.azure.resourcemanager.resources.models.StatusMessage;
import com.azure.resourcemanager.resources.models.TargetResource;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AzureFailureDiagnostics {

    private AzureFailureDiagnostics() {
    }

    public static AzureFailureSnapshot summarize(
        String prefix,
        ManagementError error,
        Integer statusCode,
        String requestId,
        String armServiceRequestId,
        String correlationId,
        String deploymentName,
        String operationId,
        String resourceId,
        List<? extends ResourceReferenceExtended> failedResources,
        DeploymentOperationInner failedOperation
    ) {
        ErrorCandidate primary = primaryCandidate(error, failedResources, failedOperation);
        String message = firstNonBlank(primary.message(), prefix);
        String detailText = detailText(prefix, error, failedResources, failedOperation);
        DeploymentOperationProperties failedOperationProperties = failedOperation == null ? null : failedOperation.properties();

        String operationRequestId = failedOperationProperties == null
            ? ""
            : normalize(failedOperationProperties.serviceRequestId());
        String mostSpecificResourceId = firstNonBlank(
            targetResourceId(failedOperationProperties == null ? null : failedOperationProperties.targetResource()),
            firstFailedResourceId(failedResources),
            resourceId
        );

        StageErrorDetailsRecord details = new StageErrorDetailsRecord(
            statusCode,
            nullable(firstNonBlank(detailText, message)),
            null,
            nullable(primary.code()),
            nullable(message),
            nullable(firstNonBlank(requestId, operationRequestId)),
            nullable(firstNonBlank(armServiceRequestId, operationRequestId)),
            nullable(correlationId),
            nullable(deploymentName),
            nullable(firstNonBlank(operationId, failedOperation == null ? null : failedOperation.operationId())),
            nullable(mostSpecificResourceId)
        );
        return new AzureFailureSnapshot(message, details);
    }

    private static ErrorCandidate primaryCandidate(
        ManagementError error,
        List<? extends ResourceReferenceExtended> failedResources,
        DeploymentOperationInner failedOperation
    ) {
        ManagementError failedOperationError = failedOperationError(failedOperation);
        if (failedOperationError != null) {
            ManagementError specific = mostSpecificError(failedOperationError);
            return new ErrorCandidate(
                normalize(specific.getCode()),
                normalize(specific.getMessage()),
                normalize(specific.getTarget())
            );
        }

        ResourceReferenceExtended failedResource = firstFailedResource(failedResources);
        if (failedResource != null && failedResource.error() != null) {
            ManagementError specific = mostSpecificError(failedResource.error());
            return new ErrorCandidate(
                normalize(specific.getCode()),
                normalize(specific.getMessage()),
                firstNonBlank(normalize(specific.getTarget()), normalize(failedResource.id()))
            );
        }

        if (error != null) {
            ManagementError specific = mostSpecificError(error);
            return new ErrorCandidate(
                normalize(specific.getCode()),
                normalize(specific.getMessage()),
                normalize(specific.getTarget())
            );
        }

        return new ErrorCandidate("", "", "");
    }

    private static String detailText(
        String prefix,
        ManagementError error,
        List<? extends ResourceReferenceExtended> failedResources,
        DeploymentOperationInner failedOperation
    ) {
        Set<String> lines = new LinkedHashSet<>();

        if (error != null) {
            collectErrorLines(lines, "root", error);
        }

        if (failedOperation != null && failedOperation.properties() != null) {
            DeploymentOperationProperties properties = failedOperation.properties();
            ManagementError operationError = failedOperationError(failedOperation);
            if (operationError != null) {
                String target = targetResourceId(properties.targetResource());
                lines.add(
                    formatLine(
                        "failed operation",
                        firstNonBlank(target, normalize(failedOperation.operationId())),
                        mostSpecificError(operationError)
                    )
                );
            }
        }

        if (failedResources != null) {
            int emitted = 0;
            for (ResourceReferenceExtended failedResource : failedResources) {
                if (failedResource == null || failedResource.error() == null) {
                    continue;
                }
                lines.add(formatLine("failed resource", normalize(failedResource.id()), mostSpecificError(failedResource.error())));
                emitted += 1;
                if (emitted >= 3) {
                    break;
                }
            }
        }

        if (lines.isEmpty()) {
            return normalize(prefix);
        }
        return String.join("\n", lines);
    }

    private static void collectErrorLines(Set<String> lines, String label, ManagementError error) {
        if (error == null) {
            return;
        }
        lines.add(formatLine(label, normalize(error.getTarget()), error));
        List<? extends ManagementError> details = error.getDetails();
        if (details == null) {
            return;
        }
        for (ManagementError detail : details) {
            collectErrorLines(lines, "detail", detail);
        }
    }

    private static String formatLine(String label, String subject, ManagementError error) {
        List<String> parts = new ArrayList<>();
        parts.add(label + ":");
        String code = normalize(error.getCode());
        String message = normalize(error.getMessage());
        String target = firstNonBlank(normalize(error.getTarget()), subject);
        if (!code.isBlank()) {
            parts.add("[" + code + "]");
        }
        if (!message.isBlank()) {
            parts.add(message);
        }
        if (!target.isBlank()) {
            parts.add("(target: " + target + ")");
        }
        return String.join(" ", parts).trim();
    }

    private static ManagementError mostSpecificError(ManagementError error) {
        if (error == null) {
            return null;
        }
        List<? extends ManagementError> details = error.getDetails();
        if (details != null) {
            for (ManagementError detail : details) {
                ManagementError candidate = mostSpecificError(detail);
                if (candidate != null && hasText(candidate)) {
                    return candidate;
                }
            }
        }
        return error;
    }

    private static boolean hasText(ManagementError error) {
        return error != null
            && (!normalize(error.getCode()).isBlank()
            || !normalize(error.getMessage()).isBlank()
            || !normalize(error.getTarget()).isBlank());
    }

    private static ManagementError failedOperationError(DeploymentOperationInner failedOperation) {
        if (failedOperation == null || failedOperation.properties() == null) {
            return null;
        }
        StatusMessage statusMessage = failedOperation.properties().statusMessage();
        return statusMessage == null ? null : statusMessage.error();
    }

    private static ResourceReferenceExtended firstFailedResource(List<? extends ResourceReferenceExtended> failedResources) {
        if (failedResources == null) {
            return null;
        }
        for (ResourceReferenceExtended failedResource : failedResources) {
            if (failedResource != null && failedResource.error() != null) {
                return failedResource;
            }
        }
        return null;
    }

    private static String firstFailedResourceId(List<? extends ResourceReferenceExtended> failedResources) {
        ResourceReferenceExtended failedResource = firstFailedResource(failedResources);
        return failedResource == null ? "" : normalize(failedResource.id());
    }

    private static String targetResourceId(TargetResource targetResource) {
        return targetResource == null ? "" : normalize(targetResource.id());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private static String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record AzureFailureSnapshot(String message, StageErrorDetailsRecord details) {
    }

    private record ErrorCandidate(String code, String message, String target) {
    }
}
