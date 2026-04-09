package com.mappo.controlplane.service.run;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.persistence.target.TargetRecordQueryRepository;
import com.mappo.controlplane.service.ReleaseService;
import com.mappo.controlplane.infrastructure.azure.auth.AzureExecutorClient;
import com.mappo.controlplane.service.project.ProjectExecutionCapabilityResolver;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunRequestResolverService {

    private final TargetRecordQueryRepository targetRecordQueryRepository;
    private final ReleaseService releaseService;
    private final ProjectExecutionCapabilityResolver projectExecutionCapabilityResolver;
    private final AzureExecutorClient azureExecutorClient;

    public RunRequestContext resolve(RunCreateRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run request is required");
        }
        CreateRunCommand command = request.toCommand();
        String releaseId = command.releaseId();
        if (releaseId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "releaseId is required");
        }

        ReleaseRecord release = releaseService.getRelease(releaseId);
        List<TargetRecord> targets = resolveTargets(command, release.projectId());
        if (targets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "no matching targets found");
        }
        return new RunRequestContext(
            command,
            projectExecutionCapabilityResolver.resolve(release, azureExecutorClient.isConfigured()),
            release,
            targets
        );
    }

    private List<TargetRecord> resolveTargets(CreateRunCommand request, String projectId) {
        if (!request.targetIds().isEmpty()) {
            List<TargetRecord> targets = targetRecordQueryRepository.getTargetsByIdsForProject(request.targetIds(), projectId);
            if (targets.size() != request.targetIds().size()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "one or more selected targets do not belong to the release project");
            }
            return targets;
        }

        if (!request.targetTags().isEmpty()) {
            return targetRecordQueryRepository.getTargetsByTagFiltersForProject(request.targetTags(), projectId);
        }

        return targetRecordQueryRepository.getTargetsByTagFiltersForProject(Map.of(), projectId);
    }
}
