package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunPreviewRecord;
import com.mappo.controlplane.model.RunSummaryPageRecord;
import com.mappo.controlplane.model.RunSummaryRecord;
import com.mappo.controlplane.model.query.RunPageQuery;
import com.mappo.controlplane.service.RunPreviewService;
import com.mappo.controlplane.service.RunService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunsController {

    private final RunService runService;
    private final RunPreviewService runPreviewService;

    @GetMapping
    public RunSummaryPageRecord listRuns(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "25") int size,
        @RequestParam(value = "runId", required = false) String runId,
        @RequestParam(value = "releaseId", required = false) String releaseId,
        @RequestParam(value = "status", required = false) String status
    ) {
        return runService.listRunsPage(new RunPageQuery(page, size, runId, releaseId, status));
    }

    @GetMapping("/{runId}")
    public RunDetailRecord getRun(@PathVariable("runId") String runId) {
        return runService.getRun(runId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunDetailRecord createRun(@Valid @RequestBody RunCreateRequest request) {
        return runService.createRun(request);
    }

    @PostMapping("/preview")
    public RunPreviewRecord previewRun(@Valid @RequestBody RunCreateRequest request) {
        return runPreviewService.previewRun(request);
    }

    @PostMapping("/{runId}/resume")
    public RunDetailRecord resumeRun(@PathVariable("runId") String runId) {
        return runService.resumeRun(runId);
    }

    @PostMapping("/{runId}/retry-failed")
    public RunDetailRecord retryFailed(@PathVariable("runId") String runId) {
        return runService.retryFailed(runId);
    }
}
