package com.mappo.controlplane.service;

import com.mappo.controlplane.model.TargetPageRecord;
import com.mappo.controlplane.model.query.TargetPageQuery;
import com.mappo.controlplane.repository.TargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TargetService {

    private final TargetRepository repository;

    public TargetPageRecord listTargetsPage(TargetPageQuery query) {
        return repository.listTargetsPage(query);
    }
}
