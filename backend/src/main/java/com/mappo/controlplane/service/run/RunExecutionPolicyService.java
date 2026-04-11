package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoStrategyMode;
import com.mappo.controlplane.model.RunExecutionCountsRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunStopPolicyRecord;
import com.mappo.controlplane.model.TargetRecord;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunExecutionPolicyService {

    private final DeploymentDriverRegistry deploymentDriverRegistry;

    public List<List<TargetRecord>> planBatches(RunDetailRecord run, List<TargetRecord> targets) {
        int concurrency = normalizeConcurrency(run.concurrency());
        if (run.strategyMode() == MappoStrategyMode.waves) {
            return planWaveBatches(targets, run.waveTag(), run.waveOrder(), concurrency);
        }
        return chunkTargets(targets, concurrency);
    }

    public List<String> buildWarnings(ProjectDefinition project, ReleaseRecord release, List<TargetRecord> targets, boolean runtimeConfigured) {
        List<String> warnings = new ArrayList<>();
        if (hasDeploymentDriver(project, release, runtimeConfigured)) {
            if (release.executionSettings().whatIfOnCanary()) {
                warnings.add("whatIfOnCanary is configured but not enforced during deployment yet; deployment will proceed directly.");
            }
            return warnings;
        }

        if (!runtimeConfigured) {
            warnings.add("Deployment runtime is not configured; run completed in simulator mode for source type " + sourceTypeLiteral(release) + ".");
            return warnings;
        }

        warnings.add("No deployment driver supports source type " + sourceTypeLiteral(release) + "; run completed in simulator mode.");
        if (hasTagValue(targets, "ring", "canary") && release.executionSettings().whatIfOnCanary()) {
            warnings.add("whatIfOnCanary is configured but not implemented in simulator mode.");
        }
        return warnings;
    }

    public String verificationMessage(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return deploymentDriverRegistry.findDriver(project, release, runtimeConfigured)
            .map(driver -> driver.verificationMessage(project, release))
            .orElse("Verification passed in simulator mode.");
    }

    public boolean isSimulatorMode(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return !hasDeploymentDriver(project, release, runtimeConfigured);
    }

    public String haltReasonFor(RunStopPolicyRecord stopPolicy, int failedCount, int processedCount, int totalTargets) {
        if (stopPolicy == null) {
            return null;
        }
        if (processedCount >= totalTargets) {
            return null;
        }
        if (stopPolicy.maxFailureCount() != null && failedCount >= stopPolicy.maxFailureCount()) {
            return "failed targets " + failedCount + " reached threshold " + stopPolicy.maxFailureCount() + ".";
        }
        if (stopPolicy.maxFailureRate() != null && processedCount > 0) {
            double rate = (double) failedCount / (double) processedCount;
            if (rate >= stopPolicy.maxFailureRate()) {
                return "failure rate " + failedCount + "/" + processedCount + " reached threshold " + stopPolicy.maxFailureRate() + ".";
            }
        }
        return null;
    }

    public MappoRunStatus finalRunStatus(
        int targetCount,
        int succeededCount,
        int failedCount,
        String haltReason,
        int processedCount
    ) {
        if (haltReason != null && processedCount < targetCount) {
            return MappoRunStatus.halted;
        }
        if (failedCount == 0) {
            return MappoRunStatus.succeeded;
        }
        if (succeededCount == 0 && failedCount >= targetCount) {
            return MappoRunStatus.failed;
        }
        return MappoRunStatus.partial;
    }

    public MappoRunStatus finalRunStatus(RunExecutionCountsRecord counts, String haltReason) {
        if (haltReason != null && counts.hasQueuedTargets()) {
            return MappoRunStatus.halted;
        }
        if (counts.failedTargets() == 0 && !counts.hasActiveTargets() && !counts.hasQueuedTargets()) {
            return MappoRunStatus.succeeded;
        }
        if (counts.succeededTargets() == 0 && counts.failedTargets() >= counts.totalTargets()) {
            return MappoRunStatus.failed;
        }
        if (counts.hasActiveTargets() || counts.hasQueuedTargets()) {
            return MappoRunStatus.running;
        }
        return MappoRunStatus.partial;
    }

    private List<List<TargetRecord>> planWaveBatches(
        List<TargetRecord> targets,
        String waveTag,
        List<String> waveOrder,
        int concurrency
    ) {
        String normalizedWaveTag = blankToEmpty(waveTag);
        List<TargetRecord> remaining = new ArrayList<>(targets);
        List<List<TargetRecord>> batches = new ArrayList<>();

        for (String waveValue : waveOrder) {
            if (blank(waveValue)) {
                continue;
            }
            List<TargetRecord> waveTargets = new ArrayList<>();
            remaining.removeIf(target -> {
                String tagValue = target.tags().get(normalizedWaveTag);
                if (waveValue.equalsIgnoreCase(blankToEmpty(tagValue))) {
                    waveTargets.add(target);
                    return true;
                }
                return false;
            });
            batches.addAll(chunkTargets(waveTargets, concurrency));
        }

        batches.addAll(chunkTargets(remaining, concurrency));
        return batches;
    }

    private List<List<TargetRecord>> chunkTargets(List<TargetRecord> targets, int concurrency) {
        List<List<TargetRecord>> batches = new ArrayList<>();
        for (int start = 0; start < targets.size(); start += concurrency) {
            int end = Math.min(start + concurrency, targets.size());
            batches.add(List.copyOf(targets.subList(start, end)));
        }
        return batches;
    }

    private int normalizeConcurrency(Integer concurrency) {
        if (concurrency == null || concurrency < 1) {
            return 1;
        }
        return concurrency;
    }

    private boolean hasTagValue(List<TargetRecord> targets, String key, String expected) {
        for (TargetRecord target : targets) {
            String value = target.tags().get(key);
            if (expected.equalsIgnoreCase(blankToEmpty(value))) {
                return true;
            }
        }
        return false;
    }

    private String sourceTypeLiteral(ReleaseRecord release) {
        return release.sourceType() == null ? "template_spec" : release.sourceType().getLiteral();
    }

    private boolean hasDeploymentDriver(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return deploymentDriverRegistry.findDriver(project, release, runtimeConfigured).isPresent();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
