package com.mappo.controlplane.service;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.repository.ReleaseRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseService {

    private final ReleaseRepository repository;
    private final LiveUpdateService liveUpdateService;

    public List<ReleaseRecord> listReleases() {
        return repository.listReleases();
    }

    public ReleaseRecord createRelease(ReleaseCreateRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release request is required");
        }
        ReleaseRecord created = repository.createRelease(request.toCommand());
        liveUpdateService.emitReleasesUpdated();
        return created;
    }

    public ReleaseRecord getRelease(String releaseId) {
        Optional<ReleaseRecord> release = repository.getRelease(releaseId);
        return release.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "release not found: " + releaseId));
    }
}
