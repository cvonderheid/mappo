package com.mappo.controlplane.service.release;

import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.repository.ReleaseCommandRepository;
import com.mappo.controlplane.repository.ReleaseQueryRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import com.mappo.controlplane.service.TransactionHookService;
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
                existingKeys.add(releaseKey(row.sourceRef(), row.sourceVersion()));
            }
        }

        int created = 0;
        int skipped = 0;
        List<String> createdReleaseIds = new ArrayList<>();
        for (var candidate : parsedManifest.requests()) {
            String key = releaseKey(candidate.sourceRef(), candidate.sourceVersion());
            if (!allowDuplicates && existingKeys.contains(key)) {
                skipped += 1;
                continue;
            }
            ReleaseRecord createdRelease = releaseCommandRepository.createRelease(candidate.toCommand());
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

    private String releaseKey(String sourceRef, String sourceVersion) {
        return normalize(sourceRef) + "::" + normalize(sourceVersion);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
