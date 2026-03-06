package com.mappo.controlplane.api;

import com.mappo.controlplane.service.RunService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunsController {

    private final RunService runService;

    @GetMapping
    public List<Map<String, Object>> listRuns() {
        return runService.listRuns();
    }

    @GetMapping("/{runId}")
    public Map<String, Object> getRun(@PathVariable("runId") String runId) {
        return runService.getRun(runId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRun(@RequestBody Map<String, Object> request) {
        return runService.createRun(request);
    }

    @PostMapping("/{runId}/resume")
    public Map<String, Object> resumeRun(@PathVariable("runId") String runId) {
        return runService.resumeRun(runId);
    }

    @PostMapping("/{runId}/retry-failed")
    public Map<String, Object> retryFailed(@PathVariable("runId") String runId) {
        return runService.retryFailed(runId);
    }
}
