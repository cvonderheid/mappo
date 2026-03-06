package com.mappo.controlplane.service;

import com.mappo.controlplane.repository.TargetRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TargetService {

    private final TargetRepository repository;

    public TargetService(TargetRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> listTargets(String ring, String region, String tier, String environment) {
        Map<String, String> filters = new LinkedHashMap<>();
        putIfPresent(filters, "ring", ring);
        putIfPresent(filters, "region", region);
        putIfPresent(filters, "tier", tier);
        putIfPresent(filters, "environment", environment);
        return repository.listTargets(filters);
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }
}
