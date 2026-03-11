package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.model.RunExecutionCountsRecord;
import com.mappo.controlplane.repository.RunExecutionStateRepository;
import com.mappo.controlplane.repository.RunLifecycleCommandRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunCompletionService {

    private final RunExecutionStateRepository runExecutionStateRepository;
    private final RunLifecycleCommandRepository runLifecycleCommandRepository;
    private final RunExecutionPolicyService runExecutionPolicyService;
    private final LiveUpdateService liveUpdateService;

    public void publishRunChange(String runId) {
        liveUpdateService.emitRunsUpdated();
        liveUpdateService.emitRunUpdated(runId);
    }

    public void finalizeRun(String runId, String haltReason) {
        RunExecutionCountsRecord counts = runExecutionStateRepository.getExecutionCounts(runId);
        MappoRunStatus finalStatus = runExecutionPolicyService.finalRunStatus(counts, haltReason);
        if (finalStatus == MappoRunStatus.running) {
            publishRunChange(runId);
            return;
        }
        runLifecycleCommandRepository.markRunComplete(runId, finalStatus, haltReason);
        publishRunChange(runId);
    }
}
