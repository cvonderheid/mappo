package com.mappo.controlplane.service;

import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.domain.execution.DeploymentPreviewDriver;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunPreviewMode;
import com.mappo.controlplane.model.RunPreviewRecord;
import com.mappo.controlplane.model.RunPreviewTargetStatus;
import com.mappo.controlplane.model.RunTargetPreviewRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.persistence.target.TargetExecutionContextRepository;
import com.mappo.controlplane.service.run.RunRequestContext;
import com.mappo.controlplane.service.run.RunRequestResolverService;
import com.mappo.controlplane.domain.execution.TargetPreviewException;
import com.mappo.controlplane.domain.execution.TargetPreviewOutcome;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunPreviewService {

    private static final String STACK_PREVIEW_CAVEAT =
        "Deployment Stack what-if is not natively available; this preview reflects the underlying ARM deployment and may not include stack unmanage or deny-setting side effects.";

    private final RunRequestResolverService runRequestResolverService;
    private final TargetExecutionContextRepository targetExecutionContextRepository;

    public RunPreviewRecord previewRun(RunCreateRequest request) {
        RunRequestContext context = runRequestResolverService.resolve(request);
        ProjectExecutionCapabilities capabilities = context.capabilities();
        boolean runtimeConfigured = capabilities.runtimeConfigured();
        List<TargetExecutionContextRecord> executionContexts = targetExecutionContextRepository.getExecutionContextsByIds(
            context.targets().stream().map(TargetRecord::id).toList()
        );
        Map<String, TargetExecutionContextRecord> contextsByTarget = new LinkedHashMap<>();
        for (TargetExecutionContextRecord executionContext : executionContexts) {
            contextsByTarget.put(executionContext.targetId(), executionContext);
        }

        RunPreviewMode mode = previewMode(capabilities);
        List<String> warnings = previewWarnings(capabilities, context.release(), runtimeConfigured);
        List<RunTargetPreviewRecord> targetPreviews = context.targets().stream()
            .map(target -> previewTarget(capabilities, context.release(), target, contextsByTarget.get(target.id()), mode))
            .toList();

        return new RunPreviewRecord(
            context.release().id(),
            context.release().sourceVersion(),
            mode,
            mode == RunPreviewMode.ARM_WHAT_IF ? STACK_PREVIEW_CAVEAT : "Preview is unavailable for the selected release execution mode.",
            warnings,
            targetPreviews
        );
    }

    private RunTargetPreviewRecord previewTarget(
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        RunPreviewMode mode
    ) {
        if (context == null) {
            return failedPreview(target, "Target is missing registration metadata required for preview.");
        }

        if (mode == RunPreviewMode.UNSUPPORTED) {
            return new RunTargetPreviewRecord(
                target.id(),
                firstNonBlank(target.customerName(), target.id()),
                target.tags().get("ring"),
                context.managedResourceGroupId(),
                RunPreviewTargetStatus.UNSUPPORTED,
                unsupportedSummary(release),
                List.of(),
                null,
                List.of()
            );
        }

        try {
            TargetAccessValidation validation = capabilities.targetAccessResolver()
                .validate(capabilities.project(), release, target, context, capabilities.runtimeConfigured());
            if (!validation.valid()) {
                return new RunTargetPreviewRecord(
                    target.id(),
                    firstNonBlank(target.customerName(), target.id()),
                    target.tags().get("ring"),
                    context.managedResourceGroupId(),
                    RunPreviewTargetStatus.FAILED,
                    validation.message(),
                    List.of(),
                    validation.error(),
                    List.of()
                );
            }
            DeploymentPreviewDriver previewDriver = capabilities.previewDriver()
                .orElseThrow(() -> new IllegalStateException("preview driver not found for supported release"));
            TargetPreviewOutcome outcome = previewDriver.preview(
                capabilities.project(),
                release,
                context,
                validation.accessContext()
            );
            return new RunTargetPreviewRecord(
                target.id(),
                firstNonBlank(target.customerName(), target.id()),
                target.tags().get("ring"),
                context.managedResourceGroupId(),
                RunPreviewTargetStatus.PREVIEWED,
                outcome.summary(),
                outcome.warnings(),
                null,
                outcome.changes()
            );
        } catch (IllegalArgumentException error) {
            return failedPreview(target, context, error.getMessage(), error.getMessage());
        } catch (TargetPreviewException error) {
            return new RunTargetPreviewRecord(
                target.id(),
                firstNonBlank(target.customerName(), target.id()),
                target.tags().get("ring"),
                context.managedResourceGroupId(),
                RunPreviewTargetStatus.FAILED,
                error.getMessage(),
                List.of(),
                error.getError(),
                List.of()
            );
        }
    }

    private RunTargetPreviewRecord failedPreview(TargetRecord target, String message) {
        return failedPreview(target, null, message, message);
    }

    private RunTargetPreviewRecord failedPreview(
        TargetRecord target,
        TargetExecutionContextRecord context,
        String message,
        String detail
    ) {
        return new RunTargetPreviewRecord(
            target.id(),
            firstNonBlank(target.customerName(), target.id()),
            target.tags().get("ring"),
            context == null ? null : context.managedResourceGroupId(),
            RunPreviewTargetStatus.FAILED,
            message,
            List.of(),
            new StageErrorRecord(
                "RUN_PREVIEW_CONFIGURATION_INVALID",
                message,
                new StageErrorDetailsRecord(
                    null,
                    detail,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    context == null ? null : context.containerAppResourceId()
                )
            ),
            List.of()
        );
    }

    private RunPreviewMode previewMode(ProjectExecutionCapabilities capabilities) {
        return capabilities.previewDriver()
            .map(DeploymentPreviewDriver::mode)
            .orElse(RunPreviewMode.UNSUPPORTED);
    }

    private List<String> previewWarnings(
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        boolean runtimeConfigured
    ) {
        if (!runtimeConfigured) {
            return List.of("Deployment runtime is not configured; run preview is unavailable.");
        }
        if (capabilities.previewDriver().isEmpty()) {
            return List.of(
                "Preview is not available for source type "
                    + release.sourceType().getLiteral()
                    + " at deployment scope "
                    + release.deploymentScope().getLiteral()
                    + "."
            );
        }
        return List.of();
    }

    private String unsupportedSummary(ReleaseRecord release) {
        return "Preview is not implemented for source type "
            + release.sourceType().getLiteral()
            + " at deployment scope "
            + release.deploymentScope().getLiteral()
            + ".";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
