package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunExecutionCountsRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.persistence.run.RunExecutionStateRepository;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunExecutionService {

    private final RunExecutionStateRepository runExecutionStateRepository;
    private final RunExecutionPolicyService runExecutionPolicyService;
    private final RunExecutionContextResolver runExecutionContextResolver;
    private final RunPreparationService runPreparationService;
    private final RunBatchExecutionService runBatchExecutionService;
    private final RunCompletionService runCompletionService;

    public void executeRun(String runId) {
        RunExecutionContext context = runExecutionContextResolver.resolve(runId);
        boolean runtimeConfigured = context.capabilities().runtimeConfigured();
        RunDetailRecord run = context.run();
        String projectId = context.release().projectId();
        if (run.status() != com.mappo.controlplane.jooq.enums.MappoRunStatus.running) {
            runCompletionService.publishRunChange(projectId, runId);
            return;
        }

        runPreparationService.failMissingTargets(context);
        runPreparationService.persistWarnings(context, runtimeConfigured);
        runCompletionService.publishRunChange(projectId, runId);

        String haltReason = null;
        if (context.executableTargets().isEmpty()) {
            runCompletionService.finalizeRun(runId, projectId, haltReason);
            return;
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<List<TargetRecord>> batches = runExecutionPolicyService.planBatches(run, context.executableTargets());
            for (List<TargetRecord> batch : batches) {
                runBatchExecutionService.executeBatch(
                    executor,
                    runId,
                    context.capabilities(),
                    context.release(),
                    batch,
                    context.executionContextsByTarget(),
                    runtimeConfigured
                );
                RunExecutionCountsRecord counts = runExecutionStateRepository.getExecutionCounts(runId);
                haltReason = runExecutionPolicyService.haltReasonFor(
                    run.stopPolicy(),
                    counts.failedTargets(),
                    counts.processedTargets(),
                    counts.totalTargets()
                );
                if (haltReason != null && counts.hasQueuedTargets()) {
                    break;
                }
            }
        }

        runCompletionService.finalizeRun(runId, projectId, haltReason);
    }
}
