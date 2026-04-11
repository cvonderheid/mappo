package com.mappo.controlplane.service.run;

import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.RunDetailRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.persistence.release.ReleaseQueryRepository;
import com.mappo.controlplane.persistence.run.RunDetailQueryRepository;
import com.mappo.controlplane.persistence.run.RunExecutionStateRepository;
import com.mappo.controlplane.persistence.target.TargetExecutionContextRepository;
import com.mappo.controlplane.persistence.target.TargetRecordQueryRepository;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilities;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilityResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunExecutionContextResolver {

    private final RunDetailQueryRepository runDetailQueryRepository;
    private final ReleaseQueryRepository releaseQueryRepository;
    private final RunExecutionStateRepository runExecutionStateRepository;
    private final TargetRecordQueryRepository targetRecordQueryRepository;
    private final TargetExecutionContextRepository targetExecutionContextRepository;
    private final ProjectExecutionCapabilityResolver projectExecutionCapabilityResolver;

    public RunExecutionContext resolve(String runId) {
        RunDetailRecord run = runDetailQueryRepository.getRunDetail(runId)
            .orElseThrow(() -> new IllegalStateException("run not found: " + runId));

        ReleaseRecord release = releaseQueryRepository.getRelease(run.releaseId())
            .orElseThrow(() -> new IllegalStateException("release not found: " + run.releaseId()));

        ProjectExecutionCapabilities capabilities = projectExecutionCapabilityResolver.resolve(release);
        List<String> queuedTargetIds = runExecutionStateRepository.listTargetIdsByStatuses(
            runId,
            List.of(MappoTargetStage.QUEUED)
        );

        Map<String, TargetRecord> targetsById = new LinkedHashMap<>();
        for (TargetRecord target : targetRecordQueryRepository.getTargetsByIdsForProject(queuedTargetIds, release.projectId())) {
            targetsById.put(target.id(), target);
        }

        List<String> missingTargetIds = new ArrayList<>();
        List<TargetRecord> executableTargets = new ArrayList<>(queuedTargetIds.size());
        for (String queuedTargetId : queuedTargetIds) {
            TargetRecord target = targetsById.get(queuedTargetId);
            if (target == null) {
                missingTargetIds.add(queuedTargetId);
                continue;
            }
            executableTargets.add(target);
        }

        return new RunExecutionContext(
            runId,
            run,
            release,
            capabilities,
            queuedTargetIds,
            executableTargets,
            missingTargetIds,
            indexContexts(targetExecutionContextRepository.getExecutionContextsByIds(queuedTargetIds))
        );
    }

    private Map<String, TargetExecutionContextRecord> indexContexts(List<TargetExecutionContextRecord> contexts) {
        Map<String, TargetExecutionContextRecord> index = new LinkedHashMap<>();
        for (TargetExecutionContextRecord context : contexts) {
            index.put(context.targetId(), context);
        }
        return index;
    }
}
