package com.mappo.controlplane.service;

import com.mappo.controlplane.model.TargetPageRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.query.TargetPageQuery;
import com.mappo.controlplane.repository.TargetRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TargetService {

    private final TargetRepository repository;

    public List<TargetRecord> listTargets(String ring, String region, String tier, String environment) {
        Map<String, String> filters = new LinkedHashMap<>();
        putIfPresent(filters, "ring", ring);
        putIfPresent(filters, "region", region);
        putIfPresent(filters, "tier", tier);
        putIfPresent(filters, "environment", environment);
        return repository.listTargets(filters);
    }

    public TargetPageRecord listTargetsPage(TargetPageQuery query) {
        return repository.listTargetsPage(query);
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }
}
