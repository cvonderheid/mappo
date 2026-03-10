package com.mappo.controlplane.service;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.model.RunExecutionCountsRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunSummaryPageRecord;
import com.mappo.controlplane.model.RunSummaryRecord;
import com.mappo.controlplane.model.query.RunPageQuery;
import com.mappo.controlplane.repository.RunCommandRepository;
import com.mappo.controlplane.repository.RunRepository;
import com.mappo.controlplane.service.run.RunDispatchService;
import com.mappo.controlplane.service.run.RunRequestContext;
import com.mappo.controlplane.service.run.RunRequestResolverService;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RunService {

    private final RunRepository runRepository;
    private final RunCommandRepository runCommandRepository;
    private final RunDispatchService runDispatchService;
    private final RunRequestResolverService runRequestResolverService;
    private final LiveUpdateService liveUpdateService;
    private final TransactionHookService transactionHookService;

    public List<RunSummaryRecord> listRuns() {
        return runRepository.listRunSummaries();
    }

    public RunSummaryPageRecord listRunsPage(RunPageQuery query) {
        return runRepository.listRunSummariesPage(query);
    }

    public RunDetailRecord getRun(String runId) {
        return runRepository.getRunDetail(runId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run not found: " + runId));
    }

    @Transactional
    public RunDetailRecord createRun(RunCreateRequest request) {
        RunRequestContext context = runRequestResolverService.resolve(request);
        CreateRunCommand command = context.command();

        String runId = "run-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        MappoReleaseSourceType executionSourceType = context.release().sourceType();
        runCommandRepository.createRun(runId, command, context.targets(), executionSourceType);
        RunDetailRecord initialRun = getRun(runId);
        transactionHookService.afterCommitOrNow(() -> {
            liveUpdateService.emitRunsUpdated();
            liveUpdateService.emitRunUpdated(runId);
            runDispatchService.dispatchRun(runId);
        });
        return initialRun;
    }

    @Transactional
    public RunDetailRecord resumeRun(String runId) {
        RunDetailRecord detail = getRun(runId);
        if (detail.status() != MappoRunStatus.halted) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run is not resumable");
        }
        List<String> requeuedTargetIds = runCommandRepository.requeueActiveTargets(
            runId,
            "Queued after execution recovery or resume."
        );
        RunExecutionCountsRecord counts = runCommandRepository.getExecutionCounts(runId);
        if (!counts.hasQueuedTargets() && requeuedTargetIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run has no queued targets to resume");
        }
        runCommandRepository.markRunRunning(runId, null);
        transactionHookService.afterCommitOrNow(() -> {
            liveUpdateService.emitRunsUpdated();
            liveUpdateService.emitRunUpdated(runId);
            runDispatchService.dispatchRun(runId);
        });
        return getRun(runId);
    }

    @Transactional
    public RunDetailRecord retryFailed(String runId) {
        RunDetailRecord detail = getRun(runId);
        if (detail.status() == MappoRunStatus.running) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run is already executing");
        }
        long failed = detail.targetRecords().stream()
            .filter(row -> row.status() == MappoTargetStage.FAILED)
            .count();
        if (failed == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run has no failed targets to retry");
        }
        runCommandRepository.requeueFailedTargets(runId, "Queued for retry after failed deployment.");
        runCommandRepository.requeueActiveTargets(runId, "Queued after execution recovery or retry.");
        runCommandRepository.markRunRunning(runId, null);
        transactionHookService.afterCommitOrNow(() -> {
            liveUpdateService.emitRunsUpdated();
            liveUpdateService.emitRunUpdated(runId);
            runDispatchService.dispatchRun(runId);
        });
        return getRun(runId);
    }
}
