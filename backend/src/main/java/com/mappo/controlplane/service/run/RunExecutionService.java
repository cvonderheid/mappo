package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunExecutionCountsRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.repository.ReleaseQueryRepository;
import com.mappo.controlplane.repository.RunExecutionStateRepository;
import com.mappo.controlplane.repository.RunLifecycleCommandRepository;
import com.mappo.controlplane.repository.RunDetailQueryRepository;
import com.mappo.controlplane.repository.TargetCommandRepository;
import com.mappo.controlplane.repository.TargetExecutionContextRepository;
import com.mappo.controlplane.repository.TargetRecordQueryRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunExecutionService {

    private final RunDetailQueryRepository runDetailQueryRepository;
    private final RunLifecycleCommandRepository runLifecycleCommandRepository;
    private final RunExecutionStateRepository runExecutionStateRepository;
    private final ReleaseQueryRepository releaseQueryRepository;
    private final TargetRecordQueryRepository targetRecordQueryRepository;
    private final TargetExecutionContextRepository targetExecutionContextRepository;
    private final TargetCommandRepository targetCommandRepository;
    private final RunTargetExecutionService runTargetExecutionService;
    private final RunExecutionPolicyService runExecutionPolicyService;
    private final LiveUpdateService liveUpdateService;

    public void executeRun(String runId, boolean azureConfigured) {
        RunDetailRecord run = runDetailQueryRepository.getRunDetail(runId)
            .orElseThrow(() -> new IllegalStateException("run not found: " + runId));
        if (run.status() != com.mappo.controlplane.jooq.enums.MappoRunStatus.running) {
            publishRunChange(runId);
            return;
        }

        ReleaseRecord release = releaseQueryRepository.getRelease(run.releaseId())
            .orElseThrow(() -> new IllegalStateException("release not found: " + run.releaseId()));

        List<String> queuedTargetIds = runExecutionStateRepository.listTargetIdsByStatuses(
            runId,
            List.of(com.mappo.controlplane.jooq.enums.MappoTargetStage.QUEUED)
        );
        Map<String, TargetRecord> targetsById = new LinkedHashMap<>();
        for (TargetRecord target : targetRecordQueryRepository.getTargetsByIds(queuedTargetIds)) {
            targetsById.put(target.id(), target);
        }

        List<TargetExecutionContextRecord> contexts = loadExecutionContexts(queuedTargetIds);
        Map<String, TargetExecutionContextRecord> contextsByTarget = indexContexts(contexts);
        List<TargetRecord> targets = new ArrayList<>(queuedTargetIds.size());
        for (String queuedTargetId : queuedTargetIds) {
            TargetRecord target = targetsById.get(queuedTargetId);
            if (target == null) {
                runTargetExecutionService.failValidation(
                    runId,
                    queuedTargetId,
                    "Target registration is missing current metadata required for execution."
                );
                continue;
            }
            targets.add(target);
        }

        List<String> warnings = runExecutionPolicyService.buildWarnings(release, targets, azureConfigured);

        runLifecycleCommandRepository.deleteRunWarnings(runId);
        for (int i = 0; i < warnings.size(); i++) {
            runLifecycleCommandRepository.addRunWarning(runId, i, warnings.get(i));
        }
        publishRunChange(runId);

        String haltReason = null;
        if (targets.isEmpty()) {
            finalizeRun(runId, haltReason);
            return;
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<List<TargetRecord>> batches = runExecutionPolicyService.planBatches(run, targets);
            for (List<TargetRecord> batch : batches) {
                executeBatch(
                    executor,
                    runId,
                    release,
                    batch,
                    contextsByTarget,
                    azureConfigured
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

        finalizeRun(runId, haltReason);
    }

    private void executeBatch(
        java.util.concurrent.ExecutorService executor,
        String runId,
        ReleaseRecord release,
        List<TargetRecord> batch,
        Map<String, TargetExecutionContextRecord> contextsByTarget,
        boolean azureConfigured
    ) {
        List<Future<TargetRunResult>> futures = new ArrayList<>(batch.size());
        for (TargetRecord target : batch) {
            futures.add(executor.submit(() -> {
                TargetExecutionContextRecord context = contextsByTarget.get(target.id());
                boolean succeeded = runTargetExecutionService.executeTarget(
                    runId,
                    release,
                    target,
                    context,
                    azureConfigured
                );
                return new TargetRunResult(target.id(), succeeded);
            }));
        }

        for (Future<TargetRunResult> future : futures) {
            try {
                TargetRunResult result = future.get();
                if (result.succeeded()) {
                    targetCommandRepository.updateLastDeployedRelease(result.targetId(), release.sourceVersion());
                    liveUpdateService.emitTargetsUpdated();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Run execution interrupted", error);
            } catch (ExecutionException error) {
                throw new IllegalStateException("Run execution worker failed", error.getCause());
            }
        }
    }

    private List<TargetExecutionContextRecord> loadExecutionContexts(List<String> targetIds) {
        if (targetIds.isEmpty()) {
            return List.of();
        }
        return targetExecutionContextRepository.getExecutionContextsByIds(targetIds);
    }

    private Map<String, TargetExecutionContextRecord> indexContexts(List<TargetExecutionContextRecord> contexts) {
        Map<String, TargetExecutionContextRecord> index = new LinkedHashMap<>();
        for (TargetExecutionContextRecord context : contexts) {
            index.put(context.targetId(), context);
        }
        return index;
    }

    private void publishRunChange(String runId) {
        liveUpdateService.emitRunsUpdated();
        liveUpdateService.emitRunUpdated(runId);
    }

    private void finalizeRun(String runId, String haltReason) {
        RunExecutionCountsRecord counts = runExecutionStateRepository.getExecutionCounts(runId);
        var finalStatus = runExecutionPolicyService.finalRunStatus(counts, haltReason);
        if (finalStatus == com.mappo.controlplane.jooq.enums.MappoRunStatus.running) {
            publishRunChange(runId);
            return;
        }
        runLifecycleCommandRepository.markRunComplete(runId, finalStatus, haltReason);
        publishRunChange(runId);
    }

    private record TargetRunResult(String targetId, boolean succeeded) {
    }
}
