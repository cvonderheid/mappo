package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.jooq.enums.MappoStrategyMode;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.RunStopPolicyRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RunExecutionPolicyService {

    public List<List<TargetRecord>> planBatches(RunDetailRecord run, List<TargetRecord> targets) {
        int concurrency = normalizeConcurrency(run.concurrency());
        if (run.strategyMode() == MappoStrategyMode.waves) {
            return planWaveBatches(targets, run.waveTag(), run.waveOrder(), concurrency);
        }
        return chunkTargets(targets, concurrency);
    }

    public List<String> buildWarnings(ReleaseRecord release, List<TargetRecord> targets, boolean azureConfigured) {
        List<String> warnings = new ArrayList<>();
        if (useRealExecution(release, azureConfigured) && release.executionSettings().whatIfOnCanary()) {
            warnings.add("whatIfOnCanary is configured but not implemented yet; live deployment will proceed directly.");
        }
        if (useRealExecution(release, azureConfigured)) {
            return warnings;
        }

        if (!azureConfigured) {
            warnings.add("Azure execution is not configured; run completed in simulator mode for source type " + sourceTypeLiteral(release) + ".");
            return warnings;
        }

        if (release.sourceType() == MappoReleaseSourceType.template_spec) {
            warnings.add(
                "Azure execution for source type template_spec with deployment scope "
                    + release.deploymentScope().getLiteral()
                    + " is not implemented yet; run completed in simulator mode."
            );
            return warnings;
        }

        warnings.add("Azure execution for source type " + sourceTypeLiteral(release) + " is not implemented yet; run completed in simulator mode.");
        if (hasTagValue(targets, "ring", "canary") && release.executionSettings().whatIfOnCanary()) {
            warnings.add("whatIfOnCanary is configured but not implemented in simulator mode.");
        }
        return warnings;
    }

    public String validationMessage(
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        if (useRealTemplateSpecExecution(release, azureConfigured)) {
            return "Validated target " + target.id() + "; deploying into resource group " + resourceGroupName(context.managedResourceGroupId()) + ".";
        }
        if (useRealDeploymentStackExecution(release, azureConfigured)) {
            return "Validated target " + target.id() + "; updating deployment stack scope " + context.managedResourceGroupId() + ".";
        }
        return "Validated target " + target.id() + " for simulator execution.";
    }

    public String verificationMessage(ReleaseRecord release, boolean azureConfigured) {
        if (useRealTemplateSpecExecution(release, azureConfigured)) {
            return "Verification passed: ARM deployment completed successfully.";
        }
        if (useRealDeploymentStackExecution(release, azureConfigured)) {
            return "Verification passed: deployment stack completed successfully.";
        }
        return "Verification passed in simulator mode.";
    }

    public boolean useRealTemplateSpecExecution(ReleaseRecord release, boolean azureConfigured) {
        return azureConfigured
            && release.sourceType() == MappoReleaseSourceType.template_spec
            && release.deploymentScope().getLiteral().equals("resource_group");
    }

    public boolean useRealDeploymentStackExecution(ReleaseRecord release, boolean azureConfigured) {
        return azureConfigured
            && release.sourceType() == MappoReleaseSourceType.deployment_stack
            && release.deploymentScope().getLiteral().equals("resource_group");
    }

    public boolean useRealExecution(ReleaseRecord release, boolean azureConfigured) {
        return useRealTemplateSpecExecution(release, azureConfigured)
            || useRealDeploymentStackExecution(release, azureConfigured);
    }

    public boolean isSimulatorMode(ReleaseRecord release, boolean azureConfigured) {
        return !useRealExecution(release, azureConfigured);
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

    private String resourceGroupName(String resourceId) {
        String value = blankToEmpty(resourceId);
        int index = value.toLowerCase().indexOf("/resourcegroups/");
        if (index < 0) {
            return value;
        }
        String suffix = value.substring(index + "/resourceGroups/".length());
        int slash = suffix.indexOf('/');
        return slash < 0 ? suffix : suffix.substring(0, slash);
    }

    private String sourceTypeLiteral(ReleaseRecord release) {
        return release.sourceType() == null ? "template_spec" : release.sourceType().getLiteral();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
