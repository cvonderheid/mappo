package com.mappo.controlplane.integrations.github.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.service.release.ReleaseManifestDocumentReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GithubWebhookPayloadService {

    private final ReleaseManifestDocumentReader releaseManifestDocumentReader;

    public GithubWebhookPayloadService(ReleaseManifestDocumentReader releaseManifestDocumentReader) {
        this.releaseManifestDocumentReader = releaseManifestDocumentReader;
    }

    public GithubWebhookPayloadRecord parse(String rawPayload, String manifestPath) {
        Map<?, ?> payload = releaseManifestDocumentReader.readJsonObject(rawPayload, "github webhook payload is not valid JSON");
        String repo = normalize(readNestedValue(payload, "repository", "full_name"));
        if (repo.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "github webhook payload missing repository.full_name");
        }
        String ref = normalizeGithubRef(normalize(payload.get("ref")));
        List<String> changedPaths = extractChangedPaths(payload);
        return new GithubWebhookPayloadRecord(
            repo,
            ref,
            changedPaths,
            pushTouchesPath(payload, manifestPath)
        );
    }

    private Object readNestedValue(Map<?, ?> source, String parentKey, String childKey) {
        Object parent = source.get(parentKey);
        if (!(parent instanceof Map<?, ?> parentMap)) {
            return null;
        }
        return parentMap.get(childKey);
    }

    private boolean pushTouchesPath(Map<?, ?> payload, String manifestPath) {
        Object commitsValue = payload.get("commits");
        if (!(commitsValue instanceof List<?> commits)) {
            return false;
        }
        for (Object commit : commits) {
            if (!(commit instanceof Map<?, ?> commitMap)) {
                continue;
            }
            if (pathsContain(commitMap.get("added"), manifestPath)
                || pathsContain(commitMap.get("modified"), manifestPath)
                || pathsContain(commitMap.get("removed"), manifestPath)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractChangedPaths(Map<?, ?> payload) {
        Object commitsValue = payload.get("commits");
        if (!(commitsValue instanceof List<?> commits)) {
            return List.of();
        }
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (Object commit : commits) {
            if (!(commit instanceof Map<?, ?> commitMap)) {
                continue;
            }
            addPaths(paths, commitMap.get("added"));
            addPaths(paths, commitMap.get("modified"));
            addPaths(paths, commitMap.get("removed"));
        }
        return List.copyOf(paths);
    }

    private void addPaths(Set<String> target, Object value) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            String path = normalize(item).replaceFirst("^/+", "");
            if (!path.isBlank()) {
                target.add(path);
            }
        }
    }

    private boolean pathsContain(Object value, String targetPath) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (targetPath.equals(normalize(item).replaceFirst("^/+", ""))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeGithubRef(String ref) {
        String normalized = normalize(ref);
        if (normalized.startsWith("refs/heads/")) {
            return normalized.substring("refs/heads/".length());
        }
        if (normalized.startsWith("refs/tags/")) {
            return normalized.substring("refs/tags/".length());
        }
        return normalized;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
