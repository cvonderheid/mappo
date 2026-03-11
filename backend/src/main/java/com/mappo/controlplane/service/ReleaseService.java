package com.mappo.controlplane.service;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.command.CreateReleaseCommand;
import com.mappo.controlplane.repository.ReleaseCommandRepository;
import com.mappo.controlplane.repository.ReleaseQueryRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReleaseService {

    private final ReleaseQueryRepository releaseQueryRepository;
    private final ReleaseCommandRepository releaseCommandRepository;
    private final LiveUpdateService liveUpdateService;
    private final TransactionHookService transactionHookService;
    private final ProjectCatalogService projectCatalogService;

    public List<ReleaseRecord> listReleases(String projectId) {
        return releaseQueryRepository.listReleases(projectId);
    }

    @Transactional
    public ReleaseRecord createRelease(ReleaseCreateRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release request is required");
        }
        var command = request.toCommand();
        ReleaseRecord created = releaseCommandRepository.createRelease(new CreateReleaseCommand(
            projectCatalogService.resolveRequiredProjectId(command.projectId()),
            command.sourceRef(),
            command.sourceVersion(),
            command.sourceType(),
            command.sourceVersionRef(),
            command.deploymentScope(),
            command.armDeploymentMode(),
            command.whatIfOnCanary(),
            command.verifyAfterDeploy(),
            command.parameterDefaults(),
            command.releaseNotes(),
            command.verificationHints()
        ));
        transactionHookService.afterCommitOrNow(() -> liveUpdateService.emitReleasesUpdated(created.projectId()));
        return created;
    }

    public ReleaseRecord getRelease(String releaseId) {
        Optional<ReleaseRecord> release = releaseQueryRepository.getRelease(releaseId);
        return release.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "release not found: " + releaseId));
    }
}
