package com.mappo.controlplane.integrations.azuredevops.release;

import com.mappo.controlplane.service.release.ReleaseManifestDocumentReader;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AzureDevOpsReleaseWebhookPayloadService {

    private final ReleaseManifestDocumentReader releaseManifestDocumentReader;

    public AzureDevOpsReleaseWebhookPayloadService(ReleaseManifestDocumentReader releaseManifestDocumentReader) {
        this.releaseManifestDocumentReader = releaseManifestDocumentReader;
    }

    public AzureDevOpsReleaseWebhookPayloadRecord parse(
        String rawPayload,
        String eventTypeHeader,
        String deliveryIdHeader
    ) {
        Map<?, ?> payload = releaseManifestDocumentReader.readJsonObject(rawPayload, "azure devops webhook payload is not valid JSON");
        Map<?, ?> resource = map(payload.get("resource"));
        Map<?, ?> run = map(resource.get("run"));
        Map<?, ?> pipeline = map(resource.get("pipeline"));
        Map<?, ?> definition = map(resource.get("definition"));
        Map<?, ?> resourceContainers = map(payload.get("resourceContainers"));
        Map<?, ?> projectContainer = map(resourceContainers.get("project"));
        Map<?, ?> runLinks = map(run.get("_links"));
        Map<?, ?> runWebLink = map(runLinks.get("web"));
        Map<?, ?> runResources = map(run.get("resources"));
        Map<?, ?> runRepositories = map(runResources.get("repositories"));
        Map<?, ?> selfRepository = map(runRepositories.get("self"));
        Map<?, ?> resourceLinks = map(resource.get("_links"));
        Map<?, ?> resourceWebLink = map(resourceLinks.get("web"));

        String eventType = firstNonBlank(eventTypeHeader, value(payload, "eventType"), value(payload, "eventTypeName"));
        String deliveryId = firstNonBlank(
            deliveryIdHeader,
            value(payload, "id"),
            value(payload, "eventId"),
            value(payload, "messageId")
        );
        String organization = normalizeOrganization(firstNonBlank(
            value(projectContainer, "baseUrl"),
            value(projectContainer, "url")
        ));
        String project = firstNonBlank(value(projectContainer, "name"), value(resource, "projectName"));
        String pipelineId = firstNonBlank(
            value(pipeline, "id"),
            value(definition, "id"),
            value(resource, "definitionId"),
            value(resource, "pipelineId"),
            extractPipelineId(firstNonBlank(value(run, "url"), value(resource, "url")))
        );
        String pipelineName = firstNonBlank(value(pipeline, "name"), value(definition, "name"));
        String branch = normalizeRef(firstNonBlank(
            value(selfRepository, "refName"),
            value(resource, "sourceBranch"),
            value(run, "sourceBranch")
        ));
        String runId = firstNonBlank(value(run, "id"), value(resource, "id"));
        String runName = firstNonBlank(value(run, "name"), value(resource, "buildNumber"), runId);
        String runState = firstNonBlank(value(run, "state"), value(resource, "status"), value(resource, "state"));
        String runResult = firstNonBlank(value(run, "result"), value(resource, "result"));
        String runWebUrl = firstNonBlank(
            value(runWebLink, "href"),
            value(resourceWebLink, "href"),
            value(resource, "url")
        );
        String runApiUrl = firstNonBlank(value(run, "url"), value(resource, "url"));

        return new AzureDevOpsReleaseWebhookPayloadRecord(
            eventType,
            deliveryId,
            organization,
            project,
            pipelineId,
            pipelineName,
            branch,
            runId,
            runName,
            runState,
            runResult,
            runWebUrl,
            runApiUrl
        );
    }

    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> mapped ? mapped : Map.of();
    }

    private String value(Map<?, ?> source, String key) {
        Object value = source.get(key);
        return normalize(value);
    }

    private String extractPipelineId(String runApiUrl) {
        String normalized = normalize(runApiUrl);
        if (normalized.isBlank()) {
            return "";
        }
        String[] segments = normalized.split("/");
        for (int index = 0; index < segments.length - 2; index += 1) {
            if ("pipelines".equalsIgnoreCase(segments[index])) {
                return normalize(segments[index + 1]);
            }
        }
        return "";
    }

    private String normalizeOrganization(String baseUrl) {
        String normalized = normalize(baseUrl);
        if (normalized.isBlank()) {
            return "";
        }
        String withoutProtocol = normalized
            .replaceFirst("^https?://", "")
            .replaceFirst("/+$", "");
        if (withoutProtocol.startsWith("dev.azure.com/")) {
            return normalize(withoutProtocol.substring("dev.azure.com/".length()));
        }
        return withoutProtocol;
    }

    private String normalizeRef(String ref) {
        String normalized = normalize(ref);
        if (normalized.startsWith("refs/heads/")) {
            return normalized.substring("refs/heads/".length());
        }
        if (normalized.startsWith("refs/tags/")) {
            return normalized.substring("refs/tags/".length());
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
