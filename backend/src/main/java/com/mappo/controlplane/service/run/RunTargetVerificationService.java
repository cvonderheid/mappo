package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import org.springframework.stereotype.Service;

@Service
public class RunTargetVerificationService {

    private final TargetVerificationProviderRegistry targetVerificationProviderRegistry;
    private final RunTargetStageService runTargetStageService;

    public RunTargetVerificationService(
        TargetVerificationProviderRegistry targetVerificationProviderRegistry,
        RunTargetStageService runTargetStageService
    ) {
        this.targetVerificationProviderRegistry = targetVerificationProviderRegistry;
        this.runTargetStageService = runTargetStageService;
    }

    public boolean verify(
        String runId,
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        var start = runTargetStageService.beginStage(
            runId,
            target.projectId(),
            target.id(),
            MappoTargetStage.VERIFYING,
            "Verifying started."
        );
        var result = targetVerificationProviderRegistry.getProvider(
                capabilities.project(),
                release,
                target,
                context,
                azureConfigured
            )
            .verify(capabilities.project(), release, target, context, azureConfigured);
        String message = result.message();
        if (!result.succeeded()) {
            return runTargetStageService.failStage(
                runId,
                target.projectId(),
                target.id(),
                MappoTargetStage.VERIFYING,
                start.correlationId(),
                message,
                result.error()
            );
        }
        runTargetStageService.completeStage(
            runId,
            target.projectId(),
            target.id(),
            MappoTargetStage.VERIFYING,
            start,
            message,
            ""
        );
        return true;
    }
}
