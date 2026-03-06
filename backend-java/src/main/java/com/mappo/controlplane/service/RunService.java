package com.mappo.controlplane.service;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.repository.RunRepository;
import com.mappo.controlplane.repository.TargetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RunService {

    private final RunRepository runRepository;
    private final TargetRepository targetRepository;
    private final ReleaseService releaseService;
    private final AzureExecutorClient azureExecutorClient;

    public RunService(
        RunRepository runRepository,
        TargetRepository targetRepository,
        ReleaseService releaseService,
        AzureExecutorClient azureExecutorClient
    ) {
        this.runRepository = runRepository;
        this.targetRepository = targetRepository;
        this.releaseService = releaseService;
        this.azureExecutorClient = azureExecutorClient;
    }

    public List<Map<String, Object>> listRuns() {
        return runRepository.listRunSummaries();
    }

    public Map<String, Object> getRun(String runId) {
        Map<String, Object> run = runRepository.getRunDetail(runId);
        if (run.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "run not found: " + runId);
        }
        return run;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createRun(Map<String, Object> request) {
        String releaseId = stringValue(request.get("release_id"));
        if (releaseId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release_id is required");
        }

        Map<String, Object> release = releaseService.getRelease(releaseId);
        if (release.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release not found: " + releaseId);
        }

        List<Map<String, Object>> targets = resolveTargets(request);
        if (targets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "no matching targets found");
        }

        String runId = "run-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String executionMode = stringValue(release.get("deployment_mode"));
        boolean immediateSuccess = true;

        runRepository.createRun(runId, request, targets, executionMode, immediateSuccess);

        if (!"template_spec".equals(executionMode)) {
            runRepository.addRunWarning(runId, 0, "execution mode is not template_spec; run completed in simulator mode");
        } else if (!azureExecutorClient.isConfigured()) {
            runRepository.addRunWarning(runId, 0, "azure sdk credentials are not configured; run completed in simulator mode");
        }

        String releaseVersion = stringValue(release.get("template_spec_version"));
        for (Map<String, Object> target : targets) {
            targetRepository.updateLastDeployedRelease(stringValue(target.get("id")), releaseVersion);
        }

        return getRun(runId);
    }

    public Map<String, Object> resumeRun(String runId) {
        Map<String, Object> detail = getRun(runId);
        String status = stringValue(detail.get("status"));
        if (!"halted".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run is not resumable");
        }
        runRepository.markRunComplete(runId, "succeeded", null);
        return getRun(runId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> retryFailed(String runId) {
        Map<String, Object> detail = getRun(runId);
        List<Map<String, Object>> records = (List<Map<String, Object>>) detail.getOrDefault("target_records", List.of());
        long failed = records.stream()
            .filter(row -> "FAILED".equalsIgnoreCase(String.valueOf(row.get("status"))))
            .count();
        if (failed == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run has no failed targets to retry");
        }
        runRepository.markRunComplete(runId, "succeeded", null);
        return getRun(runId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveTargets(Map<String, Object> request) {
        Object targetIdsObj = request.get("target_ids");
        if (targetIdsObj instanceof List<?> rawTargetIds && !rawTargetIds.isEmpty()) {
            List<String> targetIds = rawTargetIds.stream().map(String::valueOf).toList();
            return targetRepository.getTargetsByIds(targetIds);
        }

        Object targetTagsObj = request.get("target_tags");
        if (targetTagsObj instanceof Map<?, ?> rawTags && !rawTags.isEmpty()) {
            List<Map.Entry<?, ?>> entries = new ArrayList<>(rawTags.entrySet());
            java.util.LinkedHashMap<String, String> tags = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : entries) {
                String key = String.valueOf(entry.getKey()).trim();
                String value = String.valueOf(entry.getValue()).trim();
                if (!key.isBlank() && !value.isBlank()) {
                    tags.put(key, value);
                }
            }
            return targetRepository.getTargetsByTagFilters(tags);
        }

        return targetRepository.listTargets(Map.of());
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
