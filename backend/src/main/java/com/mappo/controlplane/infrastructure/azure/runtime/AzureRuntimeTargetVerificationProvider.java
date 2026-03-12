package com.mappo.controlplane.infrastructure.azure.runtime;

import com.mappo.controlplane.domain.health.TargetVerificationProvider;
import com.mappo.controlplane.domain.project.AzureContainerAppHttpRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import com.mappo.controlplane.model.TargetVerificationResultRecord;
import com.mappo.controlplane.service.runtime.RuntimeHealthProviderRegistry;
import com.mappo.controlplane.service.runtime.TargetRuntimeProbeExecutionService;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Primary
@Order(100)
public class AzureRuntimeTargetVerificationProvider implements TargetVerificationProvider {

    private final MappoProperties properties;
    private final RuntimeHealthProviderRegistry runtimeHealthProviderRegistry;
    private final TargetRuntimeProbeExecutionService targetRuntimeProbeExecutionService;

    public AzureRuntimeTargetVerificationProvider(
        MappoProperties properties,
        RuntimeHealthProviderRegistry runtimeHealthProviderRegistry,
        TargetRuntimeProbeExecutionService targetRuntimeProbeExecutionService
    ) {
        this.properties = properties;
        this.runtimeHealthProviderRegistry = runtimeHealthProviderRegistry;
        this.targetRuntimeProbeExecutionService = targetRuntimeProbeExecutionService;
    }

    @Override
    public boolean supports(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        if (!properties.getRuntimeProbe().isEnabled()
            || !azureConfigured
            || project.runtimeHealthProvider() != ProjectRuntimeHealthProviderType.azure_container_app_http
            || !release.executionSettings().verifyAfterDeploy()) {
            return false;
        }
        TargetRuntimeProbeContextRecord probeContext = toProbeContext(project, target, context);
        return probeContext != null && runtimeHealthProviderRegistry.findConfigured(probeContext).isPresent();
    }

    @Override
    public TargetVerificationResultRecord verify(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        TargetRuntimeProbeContextRecord probeContext = toProbeContext(project, target, context);
        if (probeContext == null) {
            String message = "Runtime verification could not run because the target is missing probe metadata.";
            return TargetVerificationResultRecord.failure(
                message,
                stageError("RUNTIME_PROBE_CONTEXT_MISSING", message, null, context)
            );
        }

        TargetRuntimeProbeRecord probe = targetRuntimeProbeExecutionService.probeAndPersist(probeContext)
            .orElseGet(() -> new TargetRuntimeProbeRecord(
                target.id(),
                MappoRuntimeProbeStatus.unknown,
                null,
                null,
                null,
                "Runtime verification provider is unavailable."
            ));

        if (probe.runtimeStatus() == MappoRuntimeProbeStatus.healthy) {
            return TargetVerificationResultRecord.success(
                "Runtime verification passed: " + summarizeProbe(probe)
            );
        }

        String message = "Runtime verification failed: " + summarizeProbe(probe);
        return TargetVerificationResultRecord.failure(
            message,
            stageError("RUNTIME_VERIFICATION_FAILED", message, probe, context)
        );
    }

    private TargetRuntimeProbeContextRecord toProbeContext(
        ProjectDefinition project,
        TargetRecord target,
        TargetExecutionContextRecord context
    ) {
        if (target == null || context == null) {
            return null;
        }
        UUID tenantId = context.tenantId() == null ? target.tenantId() : context.tenantId();
        UUID subscriptionId = context.subscriptionId() == null ? target.subscriptionId() : context.subscriptionId();
        String containerAppResourceId = normalize(context.containerAppResourceId());
        if (tenantId == null || subscriptionId == null || containerAppResourceId.isBlank()) {
            return null;
        }
        return new TargetRuntimeProbeContextRecord(
            target.id(),
            target.projectId(),
            tenantId,
            subscriptionId,
            containerAppResourceId,
            project.runtimeHealthProvider(),
            runtimeHealthPath(project),
            runtimeHealthExpectedStatus(project),
            runtimeHealthTimeoutMs(project),
            context.executionConfig()
        );
    }

    private String runtimeHealthPath(ProjectDefinition project) {
        if (project.runtimeHealthProviderConfig() instanceof AzureContainerAppHttpRuntimeHealthProviderConfig config) {
            return config.path();
        }
        return AzureContainerAppHttpRuntimeHealthProviderConfig.defaults().path();
    }

    private int runtimeHealthExpectedStatus(ProjectDefinition project) {
        if (project.runtimeHealthProviderConfig() instanceof AzureContainerAppHttpRuntimeHealthProviderConfig config) {
            return config.expectedStatus();
        }
        return AzureContainerAppHttpRuntimeHealthProviderConfig.defaults().expectedStatus();
    }

    private long runtimeHealthTimeoutMs(ProjectDefinition project) {
        if (project.runtimeHealthProviderConfig() instanceof AzureContainerAppHttpRuntimeHealthProviderConfig config) {
            return config.timeoutMs();
        }
        return AzureContainerAppHttpRuntimeHealthProviderConfig.defaults().timeoutMs();
    }

    private StageErrorRecord stageError(
        String code,
        String message,
        TargetRuntimeProbeRecord probe,
        TargetExecutionContextRecord context
    ) {
        return new StageErrorRecord(
            code,
            message,
            new StageErrorDetailsRecord(
                probe == null ? null : probe.httpStatusCode(),
                probe == null ? null : normalize(probe.summary()),
                null,
                code,
                message,
                null,
                null,
                null,
                null,
                null,
                context == null ? null : normalize(context.containerAppResourceId())
            )
        );
    }

    private String summarizeProbe(TargetRuntimeProbeRecord probe) {
        String summary = probe == null ? "" : normalize(probe.summary());
        if (!summary.isBlank()) {
            return summary;
        }
        if (probe != null && probe.httpStatusCode() != null) {
            return "HTTP " + probe.httpStatusCode();
        }
        if (probe != null && probe.runtimeStatus() != null) {
            return "runtime status " + probe.runtimeStatus().getLiteral();
        }
        return "no runtime probe result was produced.";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
