package com.mappo.controlplane.service.run;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.RunCreateRequest;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.command.CreateRunCommand;
import com.mappo.controlplane.repository.TargetRepository;
import com.mappo.controlplane.service.ReleaseService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunRequestResolverService {

    private final TargetRepository targetRepository;
    private final ReleaseService releaseService;

    public RunRequestContext resolve(RunCreateRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run request is required");
        }
        CreateRunCommand command = request.toCommand();
        String releaseId = command.releaseId();
        if (releaseId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "releaseId is required");
        }

        List<TargetRecord> targets = resolveTargets(command);
        if (targets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "no matching targets found");
        }

        return new RunRequestContext(
            command,
            releaseService.getRelease(releaseId),
            targets
        );
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
}
