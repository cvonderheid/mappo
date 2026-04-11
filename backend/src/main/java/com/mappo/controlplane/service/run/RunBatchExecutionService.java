package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.persistence.target.TargetCommandRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunBatchExecutionService {

    private final TargetCommandRepository targetCommandRepository;
    private final RunTargetExecutionService runTargetExecutionService;
    private final LiveUpdateService liveUpdateService;

    public void executeBatch(
        ExecutorService executor,
        String runId,
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        List<TargetRecord> batch,
        Map<String, TargetExecutionContextRecord> contextsByTarget,
        boolean runtimeConfigured
    ) {
        List<Future<TargetRunResult>> futures = new ArrayList<>(batch.size());
        for (TargetRecord target : batch) {
            futures.add(executor.submit(() -> executeTarget(
                runId,
                capabilities,
                release,
                target,
                contextsByTarget.get(target.id()),
                runtimeConfigured
            )));
        }

        for (Future<TargetRunResult> future : futures) {
            try {
                TargetRunResult result = future.get();
                if (result.succeeded()) {
                    targetCommandRepository.updateLastDeployedRelease(result.targetId(), release.sourceVersion());
                    liveUpdateService.emitTargetsUpdated(release.projectId());
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Run execution interrupted", error);
            } catch (ExecutionException error) {
                throw new IllegalStateException("Run execution worker failed", error.getCause());
            }
        }
    }

    private TargetRunResult executeTarget(
        String runId,
        ProjectExecutionCapabilities capabilities,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean runtimeConfigured
    ) {
        boolean succeeded = runTargetExecutionService.executeTarget(
            runId,
            capabilities,
            release,
            target,
            context,
            runtimeConfigured
        );
        return new TargetRunResult(target.id(), succeeded);
    }

    private record TargetRunResult(String targetId, boolean succeeded) {
    }
}
