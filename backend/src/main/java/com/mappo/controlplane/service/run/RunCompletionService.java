package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.model.RunExecutionCountsRecord;
import com.mappo.controlplane.persistence.run.RunExecutionStateRepository;
import com.mappo.controlplane.persistence.run.RunLifecycleCommandRepository;
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

    public void publishRunChange(String projectId, String runId) {
        liveUpdateService.emitRunsUpdated(projectId);
        liveUpdateService.emitRunUpdated(projectId, runId);
    }

    public void finalizeRun(String runId, String projectId, String haltReason) {
        RunExecutionCountsRecord counts = runExecutionStateRepository.getExecutionCounts(runId);
        MappoRunStatus finalStatus = runExecutionPolicyService.finalRunStatus(counts, haltReason);
        if (finalStatus == MappoRunStatus.running) {
            publishRunChange(projectId, runId);
            return;
        }
        runLifecycleCommandRepository.markRunComplete(runId, finalStatus, haltReason);
        publishRunChange(projectId, runId);
    }
}
