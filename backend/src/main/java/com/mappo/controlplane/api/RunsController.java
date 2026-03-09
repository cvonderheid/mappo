package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.api.query.RunPageParameters;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunPreviewRecord;
import com.mappo.controlplane.model.RunSummaryPageRecord;
import com.mappo.controlplane.service.RunPreviewService;
import com.mappo.controlplane.service.RunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
@Tag(name = "Runs", description = "Deployment run creation, query, preview, and retry operations.")
public class RunsController {

    private final RunService runService;
    private final RunPreviewService runPreviewService;

    @GetMapping
    @Operation(summary = "List deployment runs", description = "Primary paginated deployment-runs endpoint for the Deployments page.")
    public RunSummaryPageRecord listRuns(@Valid @ParameterObject @ModelAttribute RunPageParameters parameters) {
        return runService.listRunsPage(parameters.toQuery());
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get deployment run detail", description = "Returns the latest persisted detail for one deployment run.")
    public RunDetailRecord getRun(@PathVariable("runId") String runId) {
        return runService.getRun(runId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a deployment run", description = "Creates a run record and dispatches execution asynchronously.")
    public RunDetailRecord createRun(@Valid @RequestBody RunCreateRequest request) {
        return runService.createRun(request);
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview a deployment run", description = "Runs the current pre-deployment preview flow for the supplied rollout request.")
    public RunPreviewRecord previewRun(@Valid @RequestBody RunCreateRequest request) {
        return runPreviewService.previewRun(request);
    }

    @PostMapping("/{runId}/resume")
    @Operation(summary = "Resume a halted deployment run")
    public RunDetailRecord resumeRun(@PathVariable("runId") String runId) {
        return runService.resumeRun(runId);
    }

    @PostMapping("/{runId}/retry-failed")
    @Operation(summary = "Retry failed targets in a deployment run")
    public RunDetailRecord retryFailed(@PathVariable("runId") String runId) {
        return runService.retryFailed(runId);
    }
}
