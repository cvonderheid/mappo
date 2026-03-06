package com.mappo.controlplane.service;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunSummaryRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.repository.RunRepository;
import com.mappo.controlplane.repository.TargetRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunService {

    private final RunRepository runRepository;
    private final TargetRepository targetRepository;
    private final ReleaseService releaseService;
    private final AzureExecutorClient azureExecutorClient;

    public List<RunSummaryRecord> listRuns() {
        return runRepository.listRunSummaries();
    }

    public RunDetailRecord getRun(String runId) {
        return runRepository.getRunDetail(runId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run not found: " + runId));
    }

    public RunDetailRecord createRun(RunCreateRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run request is required");
        }
        CreateRunCommand command = request.toCommand();
        String releaseId = command.releaseId();
        if (releaseId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "releaseId is required");
        }

        ReleaseRecord release = releaseService.getRelease(releaseId);
        List<TargetRecord> targets = resolveTargets(command);
        if (targets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "no matching targets found");
        }

        String runId = "run-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        MappoReleaseSourceType executionSourceType = release.sourceType();
        boolean immediateSuccess = true;

        runRepository.createRun(runId, command, targets, executionSourceType, immediateSuccess);

        runRepository.addRunWarning(runId, 0, simulatorWarning(executionSourceType));

        String releaseVersion = release.sourceVersion();
        for (TargetRecord target : targets) {
            targetRepository.updateLastDeployedRelease(target.id(), releaseVersion);
        }

        return getRun(runId);
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

    private List<TargetRecord> resolveTargets(CreateRunCommand request) {
        if (!request.targetIds().isEmpty()) {
            return targetRepository.getTargetsByIds(request.targetIds());
        }

        if (!request.targetTags().isEmpty()) {
            return targetRepository.getTargetsByTagFilters(request.targetTags());
        }

        return targetRepository.listTargets(Map.of());
    }

    private String simulatorWarning(MappoReleaseSourceType executionSourceType) {
        String sourceType = executionSourceType == null ? "template_spec" : executionSourceType.getLiteral();
        if (!azureExecutorClient.isConfigured()) {
            return "Azure execution is not configured; run completed in simulator mode for source type " + sourceType + ".";
        }
        return "Azure execution for source type " + sourceType + " is not implemented yet; run completed in simulator mode.";
    }
}
