package com.mappo.controlplane.service.run;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.execution.RunQueue;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.persistence.run.RunExecutionStateRepository;
import com.mappo.controlplane.persistence.run.RunDetailQueryRepository;
import com.mappo.controlplane.persistence.run.RunLifecycleCommandRepository;
import com.mappo.controlplane.persistence.run.RunTargetCommandRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RunDispatchService {

    private final Executor runDispatchExecutor;
    private final RunExecutionService runExecutionService;
    private final RunLifecycleCommandRepository runLifecycleCommandRepository;
    private final RunTargetCommandRepository runTargetCommandRepository;
    private final RunExecutionStateRepository runExecutionStateRepository;
    private final RunDetailQueryRepository runDetailQueryRepository;
    private final LiveUpdateService liveUpdateService;
    private final RunQueue runQueue;
    private final MappoProperties properties;
    private final Set<String> activeRunIds = ConcurrentHashMap.newKeySet();

    public RunDispatchService(
        @Qualifier("runDispatchExecutor") Executor runDispatchExecutor,
        RunExecutionService runExecutionService,
        RunLifecycleCommandRepository runLifecycleCommandRepository,
        RunTargetCommandRepository runTargetCommandRepository,
        RunExecutionStateRepository runExecutionStateRepository,
        RunDetailQueryRepository runDetailQueryRepository,
        LiveUpdateService liveUpdateService,
        RunQueue runQueue,
        MappoProperties properties
    ) {
        this.runDispatchExecutor = runDispatchExecutor;
        this.runExecutionService = runExecutionService;
        this.runLifecycleCommandRepository = runLifecycleCommandRepository;
        this.runTargetCommandRepository = runTargetCommandRepository;
        this.runExecutionStateRepository = runExecutionStateRepository;
        this.runDetailQueryRepository = runDetailQueryRepository;
        this.liveUpdateService = liveUpdateService;
        this.runQueue = runQueue;
        this.properties = properties;
    }

    public void dispatchRun(String runId) {
        dispatchRun(runId, false);
    }

    public void forceDispatchRun(String runId) {
        dispatchRun(runId, true);
    }

    @Scheduled(
        fixedDelayString = "${mappo.redis.worker-poll-timeout-ms:1000}",
        initialDelayString = "${mappo.redis.worker-poll-timeout-ms:1000}"
    )
    public void drainQueuedRuns() {
        if (!runQueue.isEnabled()) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            String runId = runQueue.poll();
            if (runId.isBlank()) {
                return;
            }
            if (!runQueue.acquireRunLease(runId)) {
                continue;
            }
            runDispatchExecutor.execute(() -> executeQueuedRun(runId));
        }
    }

    @Scheduled(
        fixedDelayString = "${mappo.redis.heartbeat-interval-ms:10000}",
        initialDelayString = "${mappo.redis.heartbeat-interval-ms:10000}"
    )
    public void heartbeatActiveRuns() {
        if (activeRunIds.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (String runId : activeRunIds) {
            runLifecycleCommandRepository.touchRun(runId, now);
            if (runQueue.isEnabled()) {
                runQueue.renewRunLease(runId);
            }
        }
    }

    @Scheduled(
        fixedDelayString = "${mappo.redis.recovery-interval-ms:30000}",
        initialDelayString = "${mappo.redis.recovery-interval-ms:30000}"
    )
    public void recoverStaleRuns() {
        if (!runQueue.isEnabled()) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
            .minusNanos(properties.getRedis().getRecoveryStaleAfterMs() * 1_000_000L);
        for (String runId : runExecutionStateRepository.listStaleRunningRunIds(cutoff)) {
            if (activeRunIds.contains(runId) || runQueue.isRunLeaseHeld(runId)) {
                continue;
            }
            runTargetCommandRepository.requeueActiveTargets(runId, "Queued after stale execution recovery.");
            runLifecycleCommandRepository.appendRunWarning(
                runId,
                "Recovered stale running run and requeued interrupted targets."
            );
            forceDispatchRun(runId);
            String projectId = lookupProjectId(runId);
            liveUpdateService.emitRunsUpdated(projectId);
            liveUpdateService.emitRunUpdated(projectId, runId);
        }
    }

    private void dispatchRun(String runId, boolean force) {
        if (runQueue.isEnabled()) {
            if (force) {
                runQueue.enqueue(runId, true);
            } else {
                runQueue.enqueue(runId);
            }
            return;
        }
        runDispatchExecutor.execute(() -> executeQueuedRun(runId));
    }

    private void executeQueuedRun(String runId) {
        activeRunIds.add(runId);
        try {
            runExecutionService.executeRun(runId);
        } catch (RuntimeException error) {
            log.error("Run execution crashed for {}", runId, error);
            runLifecycleCommandRepository.appendRunWarning(
                runId,
                "Run execution crashed before completion: " + error.getMessage()
            );
            runLifecycleCommandRepository.markRunComplete(
                runId,
                MappoRunStatus.failed,
                "execution crashed before completion"
            );
            String projectId = lookupProjectId(runId);
            liveUpdateService.emitRunsUpdated(projectId);
            liveUpdateService.emitRunUpdated(projectId, runId);
        } finally {
            activeRunIds.remove(runId);
            if (runQueue.isEnabled()) {
                runQueue.releaseRunLease(runId);
            }
        }
    }

    private String lookupProjectId(String runId) {
        return runDetailQueryRepository.getRunDetail(runId)
            .map(detail -> detail.projectId() == null ? "" : detail.projectId())
            .orElse("");
    }
}
