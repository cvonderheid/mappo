package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.health.TargetVerificationProvider;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.TargetVerificationResultRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
public class DefaultTargetVerificationProvider implements TargetVerificationProvider {

    private final RunExecutionPolicyService runExecutionPolicyService;

    public DefaultTargetVerificationProvider(RunExecutionPolicyService runExecutionPolicyService) {
        this.runExecutionPolicyService = runExecutionPolicyService;
    }

    @Override
    public boolean supports(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean runtimeConfigured
    ) {
        return true;
    }

    @Override
    public TargetVerificationResultRecord verify(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean runtimeConfigured
    ) {
        if (runExecutionPolicyService.isSimulatorMode(project, release, runtimeConfigured)
            && target.simulatedFailureMode() == MappoSimulatedFailureMode.verify_once) {
            String message = "Simulated verification failure. Retry the run after clearing the target failure mode.";
            return TargetVerificationResultRecord.failure(
                message,
                new StageErrorRecord(
                    "SIMULATED_VERIFICATION_FAILED",
                    message,
                    new StageErrorDetailsRecord(
                        null,
                        "simulated_failure_mode=verify_once",
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
                )
            );
        }

        if (!release.executionSettings().verifyAfterDeploy()) {
            return TargetVerificationResultRecord.success("Verification skipped by release settings.");
        }

        return TargetVerificationResultRecord.success(
            runExecutionPolicyService.verificationMessage(project, release, runtimeConfigured)
        );
    }
}
