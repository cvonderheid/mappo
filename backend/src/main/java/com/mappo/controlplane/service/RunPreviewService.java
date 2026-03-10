package com.mappo.controlplane.service;

import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunPreviewMode;
import com.mappo.controlplane.model.RunPreviewRecord;
import com.mappo.controlplane.model.RunPreviewTargetStatus;
import com.mappo.controlplane.model.RunTargetPreviewRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.repository.TargetExecutionContextRepository;
import com.mappo.controlplane.service.run.DeploymentStackPreviewExecutor;
import com.mappo.controlplane.service.run.RunRequestContext;
import com.mappo.controlplane.service.run.RunRequestResolverService;
import com.mappo.controlplane.service.run.TargetPreviewException;
import com.mappo.controlplane.service.run.TargetPreviewOutcome;
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
    private final AzureExecutorClient azureExecutorClient;
    private final DeploymentStackPreviewExecutor deploymentStackPreviewExecutor;

    public RunPreviewRecord previewRun(RunCreateRequest request) {
        RunRequestContext context = runRequestResolverService.resolve(request);
        boolean azureConfigured = azureExecutorClient.isConfigured();
        List<TargetExecutionContextRecord> executionContexts = targetExecutionContextRepository.getExecutionContextsByIds(
            context.targets().stream().map(TargetRecord::id).toList()
        );
        Map<String, TargetExecutionContextRecord> contextsByTarget = new LinkedHashMap<>();
        for (TargetExecutionContextRecord executionContext : executionContexts) {
            contextsByTarget.put(executionContext.targetId(), executionContext);
        }

        RunPreviewMode mode = previewMode(context.release(), azureConfigured);
        List<String> warnings = previewWarnings(context.release(), azureConfigured);
        List<RunTargetPreviewRecord> targetPreviews = context.targets().stream()
            .map(target -> previewTarget(context.release(), target, contextsByTarget.get(target.id()), mode))
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
            TargetPreviewOutcome outcome = deploymentStackPreviewExecutor.preview(release, context);
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

    private RunPreviewMode previewMode(ReleaseRecord release, boolean azureConfigured) {
        if (azureConfigured
            && release.sourceType() == MappoReleaseSourceType.deployment_stack
            && release.deploymentScope().getLiteral().equals("resource_group")) {
            return RunPreviewMode.ARM_WHAT_IF;
        }
        return RunPreviewMode.UNSUPPORTED;
    }

    private List<String> previewWarnings(ReleaseRecord release, boolean azureConfigured) {
        if (!azureConfigured) {
            return List.of("Azure execution is not configured; run preview is unavailable.");
        }
        if (release.sourceType() != MappoReleaseSourceType.deployment_stack) {
            return List.of("Preview is currently implemented only for deployment_stack releases.");
        }
        if (!release.deploymentScope().getLiteral().equals("resource_group")) {
            return List.of("Preview is currently implemented only for resource_group deployment scope.");
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
