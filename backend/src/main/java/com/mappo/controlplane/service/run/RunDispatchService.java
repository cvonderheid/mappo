package com.mappo.controlplane.service.run;

import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.repository.RunExecutionStateRepository;
import com.mappo.controlplane.repository.RunLifecycleCommandRepository;
import com.mappo.controlplane.repository.RunTargetCommandRepository;
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
    private final LiveUpdateService liveUpdateService;
    private final AzureExecutorClient azureExecutorClient;
    private final RedisRunQueueService redisRunQueueService;
    private final MappoProperties properties;
    private final Set<String> activeRunIds = ConcurrentHashMap.newKeySet();

    public RunDispatchService(
        @Qualifier("runDispatchExecutor") Executor runDispatchExecutor,
        RunExecutionService runExecutionService,
        RunLifecycleCommandRepository runLifecycleCommandRepository,
        RunTargetCommandRepository runTargetCommandRepository,
        RunExecutionStateRepository runExecutionStateRepository,
        LiveUpdateService liveUpdateService,
        AzureExecutorClient azureExecutorClient,
        RedisRunQueueService redisRunQueueService,
        MappoProperties properties
    ) {
        this.runDispatchExecutor = runDispatchExecutor;
        this.runExecutionService = runExecutionService;
        this.runLifecycleCommandRepository = runLifecycleCommandRepository;
        this.runTargetCommandRepository = runTargetCommandRepository;
        this.runExecutionStateRepository = runExecutionStateRepository;
        this.liveUpdateService = liveUpdateService;
        this.azureExecutorClient = azureExecutorClient;
        this.redisRunQueueService = redisRunQueueService;
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
        if (!redisRunQueueService.isEnabled()) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            String runId = redisRunQueueService.poll();
            if (runId.isBlank()) {
                return;
            }
            if (!redisRunQueueService.acquireRunLease(runId)) {
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
            if (redisRunQueueService.isEnabled()) {
                redisRunQueueService.renewRunLease(runId);
            }
        }
    }

    @Scheduled(
        fixedDelayString = "${mappo.redis.recovery-interval-ms:30000}",
        initialDelayString = "${mappo.redis.recovery-interval-ms:30000}"
    )
    public void recoverStaleRuns() {
        if (!redisRunQueueService.isEnabled()) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
            .minusNanos(properties.getRedis().getRecoveryStaleAfterMs() * 1_000_000L);
        for (String runId : runExecutionStateRepository.listStaleRunningRunIds(cutoff)) {
            if (activeRunIds.contains(runId) || redisRunQueueService.isRunLeaseHeld(runId)) {
                continue;
            }
            runTargetCommandRepository.requeueActiveTargets(runId, "Queued after stale execution recovery.");
            runLifecycleCommandRepository.appendRunWarning(
                runId,
                "Recovered stale running run and requeued interrupted targets."
            );
            forceDispatchRun(runId);
            liveUpdateService.emitRunsUpdated();
            liveUpdateService.emitRunUpdated(runId);
        }
    }

    private void dispatchRun(String runId, boolean force) {
        if (redisRunQueueService.isEnabled()) {
            if (force) {
                redisRunQueueService.enqueue(runId, true);
            } else {
                redisRunQueueService.enqueue(runId);
            }
            return;
        }
        runDispatchExecutor.execute(() -> executeQueuedRun(runId));
    }

    private void executeQueuedRun(String runId) {
        activeRunIds.add(runId);
        try {
            runExecutionService.executeRun(runId, azureExecutorClient.isConfigured());
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
            liveUpdateService.emitRunsUpdated();
            liveUpdateService.emitRunUpdated(runId);
        } finally {
            activeRunIds.remove(runId);
            if (redisRunQueueService.isEnabled()) {
                redisRunQueueService.releaseRunLease(runId);
            }
        }
    }
}
