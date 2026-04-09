package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.persistence.run.RunLifecycleCommandRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunPreparationService {

    private final RunLifecycleCommandRepository runLifecycleCommandRepository;
    private final RunTargetExecutionService runTargetExecutionService;
    private final RunExecutionPolicyService runExecutionPolicyService;

    public void failMissingTargets(RunExecutionContext context) {
        for (String missingTargetId : context.missingTargetIds()) {
            runTargetExecutionService.failValidation(
                context.runId(),
                context.release().projectId(),
                missingTargetId,
                "Target registration is missing current metadata required for execution."
            );
        }
    }

    public void persistWarnings(RunExecutionContext context, boolean azureConfigured) {
        List<String> warnings = runExecutionPolicyService.buildWarnings(
            context.capabilities().project(),
            context.release(),
            context.executableTargets(),
            azureConfigured
        );

        runLifecycleCommandRepository.deleteRunWarnings(context.runId());
        for (int i = 0; i < warnings.size(); i++) {
            runLifecycleCommandRepository.addRunWarning(context.runId(), i, warnings.get(i));
        }
    }
}
