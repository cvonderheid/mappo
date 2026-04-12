package com.mappo.controlplane.service;

import com.mappo.controlplane.model.TargetPageRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRefreshResultRecord;
import com.mappo.controlplane.model.query.TargetPageQuery;
import com.mappo.controlplane.persistence.target.TargetPageQueryRepository;
import com.mappo.controlplane.service.runtime.TargetRuntimeProbeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TargetService {

    private final TargetPageQueryRepository repository;
    private final TargetRuntimeProbeService targetRuntimeProbeService;

    public TargetPageRecord listTargetsPage(TargetPageQuery query) {
        return repository.listTargetsPage(query);
    }

    public TargetRuntimeProbeRefreshResultRecord checkRuntimeHealth(String projectId) {
        return targetRuntimeProbeService.refreshRuntimeProbesForProject(projectId);
    }
}
