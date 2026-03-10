package com.mappo.controlplane.service.runtime;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import com.mappo.controlplane.repository.TargetCommandRepository;
import com.mappo.controlplane.repository.TargetRuntimeProbeContextRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    private final TargetCommandRepository targetCommandRepository;
    private final TargetRuntimeProbeClient targetRuntimeProbeClient;
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
        if (!properties.getRuntimeProbe().isEnabled() || !targetRuntimeProbeClient.isConfigured()) {
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

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<? extends Future<?>> tasks = targets.stream()
                    .map(target -> executor.submit(() -> refreshRuntimeProbe(target)))
                    .toList();
                for (Future<?> task : tasks) {
                    waitFor(task);
                }
            }
            liveUpdateService.emitTargetsUpdated();
        } finally {
            refreshInProgress.set(false);
        }
    }

    private void refreshRuntimeProbe(TargetRuntimeProbeContextRecord target) {
        try {
            targetCommandRepository.upsertRuntimeProbe(targetRuntimeProbeClient.probe(target));
        } catch (RuntimeException error) {
            targetCommandRepository.upsertRuntimeProbe(new TargetRuntimeProbeRecord(
                target.targetId(),
                MappoRuntimeProbeStatus.unknown,
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                null,
                "Runtime probe failed: " + summarizeError(error)
            ));
        }
    }

    private void waitFor(Future<?> task) {
        try {
            task.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // Probe failures are captured per-target and persisted as unknown/unreachable results.
        }
    }

    private String summarizeError(Throwable error) {
        String message = error == null ? "" : String.valueOf(error.getMessage()).trim();
        if (!message.isBlank()) {
            return message;
        }
        return error == null ? "unknown error" : error.getClass().getSimpleName();
    }
}
