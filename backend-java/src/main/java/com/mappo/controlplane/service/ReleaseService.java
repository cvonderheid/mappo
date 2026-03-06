package com.mappo.controlplane.service;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.repository.ReleaseRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseService {

    private final ReleaseRepository repository;

    public List<Map<String, Object>> listReleases() {
        return repository.listReleases();
    }

    public Map<String, Object> createRelease(Map<String, Object> request) {
        String templateSpecId = stringValue(request.get("template_spec_id"));
        String templateSpecVersion = stringValue(request.get("template_spec_version"));
        if (templateSpecId.isBlank() || templateSpecVersion.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "template_spec_id and template_spec_version are required");
        }
        return repository.createRelease(request);
    }

    public Map<String, Object> getRelease(String releaseId) {
        return repository.getRelease(releaseId);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
