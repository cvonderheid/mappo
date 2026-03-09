package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.repository.RunRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RunDispatchService {

    private final Executor runDispatchExecutor;
    private final RunExecutionService runExecutionService;
    private final RunRepository runRepository;
    private final LiveUpdateService liveUpdateService;

    public RunDispatchService(
        @Qualifier("runDispatchExecutor") Executor runDispatchExecutor,
        RunExecutionService runExecutionService,
        RunRepository runRepository,
        LiveUpdateService liveUpdateService
    ) {
        this.runDispatchExecutor = runDispatchExecutor;
        this.runExecutionService = runExecutionService;
        this.runRepository = runRepository;
        this.liveUpdateService = liveUpdateService;
    }

    public void dispatchRun(
        RunDetailRecord run,
        ReleaseRecord release,
        List<TargetRecord> targets,
        boolean azureConfigured
    ) {
        runDispatchExecutor.execute(() -> {
            try {
                runExecutionService.executeRun(run, release, targets, azureConfigured);
            } catch (RuntimeException error) {
                log.error("Run execution crashed for {}", run.id(), error);
                runRepository.appendRunWarning(
                    run.id(),
                    "Run execution crashed before completion: " + error.getMessage()
                );
                runRepository.markRunComplete(
                    run.id(),
                    MappoRunStatus.failed,
                    "execution crashed before completion"
                );
                liveUpdateService.emitRunsUpdated();
                liveUpdateService.emitRunUpdated(run.id());
            }
        });
    }
}
