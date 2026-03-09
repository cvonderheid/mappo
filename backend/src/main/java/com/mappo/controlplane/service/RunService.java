package com.mappo.controlplane.service;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunSummaryRecord;
import com.mappo.controlplane.repository.RunRepository;
import com.mappo.controlplane.service.run.RunDispatchService;
import com.mappo.controlplane.service.run.RunExecutionService;
import com.mappo.controlplane.service.run.RunRequestContext;
import com.mappo.controlplane.service.run.RunRequestResolverService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunService {

    private final RunRepository runRepository;
    private final AzureExecutorClient azureExecutorClient;
    private final RunDispatchService runDispatchService;
    private final RunRequestResolverService runRequestResolverService;

    public List<RunSummaryRecord> listRuns() {
        return runRepository.listRunSummaries();
    }

    public RunDetailRecord getRun(String runId) {
        return runRepository.getRunDetail(runId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run not found: " + runId));
    }

    public RunDetailRecord createRun(RunCreateRequest request) {
        RunRequestContext context = runRequestResolverService.resolve(request);
        CreateRunCommand command = context.command();

        String runId = "run-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        MappoReleaseSourceType executionSourceType = context.release().sourceType();
        runRepository.createRun(runId, command, context.targets(), executionSourceType);
        RunDetailRecord initialRun = getRun(runId);
        runDispatchService.dispatchRun(
            initialRun,
            context.release(),
            context.targets(),
            azureExecutorClient.isConfigured()
        );
        return initialRun;
    }

    public RunDetailRecord resumeRun(String runId) {
        RunDetailRecord detail = getRun(runId);
        if (detail.status() != MappoRunStatus.halted) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run is not resumable");
        }
        runRepository.markRunComplete(runId, MappoRunStatus.succeeded, null);
        return getRun(runId);
    }

    public RunDetailRecord retryFailed(String runId) {
        RunDetailRecord detail = getRun(runId);
        long failed = detail.targetRecords().stream()
            .filter(row -> row.status() == MappoTargetStage.FAILED)
            .count();
        if (failed == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run has no failed targets to retry");
        }
        runRepository.markRunComplete(runId, MappoRunStatus.succeeded, null);
        return getRun(runId);
    }
}
