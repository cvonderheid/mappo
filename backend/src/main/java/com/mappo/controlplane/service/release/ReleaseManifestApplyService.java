package com.mappo.controlplane.service.release;

import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.command.CreateReleaseCommand;
import com.mappo.controlplane.repository.ReleaseCommandRepository;
import com.mappo.controlplane.repository.ReleaseQueryRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import com.mappo.controlplane.service.TransactionHookService;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReleaseManifestApplyService {

    private final ReleaseQueryRepository releaseQueryRepository;
    private final ReleaseCommandRepository releaseCommandRepository;
    private final LiveUpdateService liveUpdateService;
    private final TransactionHookService transactionHookService;
    private final ProjectCatalogService projectCatalogService;

    @Transactional
    public ReleaseManifestIngestResultRecord apply(
        String repo,
        String path,
        String ref,
        boolean allowDuplicates,
        ParsedReleaseManifest parsedManifest
    ) {
        Set<String> existingKeys = new LinkedHashSet<>();
        if (!allowDuplicates) {
            for (ReleaseRecord row : releaseQueryRepository.listReleases()) {
                existingKeys.add(releaseKey(row.projectId(), row.sourceRef(), row.sourceVersion()));
            }
        }

        int created = 0;
        int skipped = 0;
        List<String> createdReleaseIds = new ArrayList<>();
        for (var candidate : parsedManifest.requests()) {
            String resolvedProjectId = projectCatalogService.resolveProjectId(candidate.projectId(), candidate.sourceType());
            String key = releaseKey(resolvedProjectId, candidate.sourceRef(), candidate.sourceVersion());
            if (!allowDuplicates && existingKeys.contains(key)) {
                skipped += 1;
                continue;
            }
            var command = candidate.toCommand();
            ReleaseRecord createdRelease = releaseCommandRepository.createRelease(new CreateReleaseCommand(
                resolvedProjectId,
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
            created += 1;
            createdReleaseIds.add(createdRelease.id());
            existingKeys.add(key);
        }

        if (created > 0) {
            transactionHookService.afterCommitOrNow(liveUpdateService::emitReleasesUpdated);
        }

        return new ReleaseManifestIngestResultRecord(
            repo,
            path,
            ref,
            parsedManifest.manifestReleaseCount(),
            created,
            skipped,
            parsedManifest.ignoredCount(),
            List.copyOf(createdReleaseIds)
        );
    }

    private String releaseKey(String projectId, String sourceRef, String sourceVersion) {
        return normalize(projectId) + "::" + normalize(sourceRef) + "::" + normalize(sourceVersion);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
