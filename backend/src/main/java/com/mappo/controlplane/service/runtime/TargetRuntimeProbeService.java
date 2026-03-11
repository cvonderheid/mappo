package com.mappo.controlplane.service.runtime;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.repository.TargetRuntimeProbeContextRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TargetRuntimeProbeService {

    private final TargetRuntimeProbeContextRepository targetRuntimeProbeContextRepository;
    private final TargetRuntimeProbeExecutionService targetRuntimeProbeExecutionService;
    private final MappoProperties properties;
    private final LiveUpdateService liveUpdateService;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    @Scheduled(
        initialDelayString = "${mappo.runtime-probe.initial-delay-ms:15000}",
        fixedDelayString = "${mappo.runtime-probe.interval-ms:60000}"
    )
    public void scheduledRefreshRuntimeProbes() {
        if (!properties.getRuntimeProbe().isEnabled()) {
            return;
        }
        refreshRuntimeProbes();
    }

    public void refreshRuntimeProbes() {
        if (!properties.getRuntimeProbe().isEnabled()) {
            return;
        }
        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            List<TargetRuntimeProbeContextRecord> targets = targetRuntimeProbeContextRepository.listRuntimeProbeContexts();
            if (targets.isEmpty()) {
                return;
            }

            Set<String> changedProjectIds = new LinkedHashSet<>();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<String>> tasks = targets.stream()
                    .map(target -> executor.submit(() -> refreshRuntimeProbe(target)))
                    .toList();
                for (Future<String> task : tasks) {
                    String changedProjectId = waitFor(task);
                    if (changedProjectId != null && !changedProjectId.isBlank()) {
                        changedProjectIds.add(changedProjectId);
                    }
                }
            }
            changedProjectIds.forEach(liveUpdateService::emitTargetsUpdated);
        } finally {
            refreshInProgress.set(false);
        }
    }

    private String refreshRuntimeProbe(TargetRuntimeProbeContextRecord target) {
        targetRuntimeProbeExecutionService.probeAndPersist(target);
        return target.projectId();
    }

    private String waitFor(Future<String> task) {
        try {
            return task.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException ignored) {
            // Probe failures are captured per-target and persisted as unknown/unreachable results.
            return null;
        }
    }
}
