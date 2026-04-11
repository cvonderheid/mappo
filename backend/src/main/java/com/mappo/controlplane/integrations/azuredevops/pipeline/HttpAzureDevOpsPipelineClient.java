package com.mappo.controlplane.integrations.azuredevops.pipeline;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.integrations.azuredevops.common.AzureDevOpsUrlNormalizer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class HttpAzureDevOpsPipelineClient implements AzureDevOpsPipelineClient {

    private final MappoProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    HttpAzureDevOpsPipelineClient(MappoProperties properties, ObjectMapper objectMapper) {
        this(
            properties,
            objectMapper,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1_000L, properties.getAzureDevOps().getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        );
    }

    HttpAzureDevOpsPipelineClient(
        MappoProperties properties,
        ObjectMapper objectMapper,
        HttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public List<AzureDevOpsProjectDefinitionRecord> listProjects(String organization, String personalAccessToken) {
        String url = projectCollectionApiUrl(organization);
        HttpResponse<String> response = sendJson(personalAccessToken, url, "GET", null);
        return toProjectDefinitions(organization, response.body());
    }

    @Override
    public AzureDevOpsPipelineRunRecord queueRun(AzureDevOpsPipelineInputs inputs) {
        String url = runCollectionApiUrl(inputs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
            "resources",
            Map.of(
                "repositories",
                Map.of(
                    "self",
                    Map.of("refName", branchRef(inputs.branch()))
                )
            )
        );
        body.put("templateParameters", inputs.templateParameters() == null ? Map.of() : inputs.templateParameters());
        HttpResponse<String> response = sendJson(inputs, url, "POST", body);
        return toRunRecord(inputs, response.body());
    }

    @Override
    public AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId) {
        String url = runItemApiUrl(inputs, normalize(runId));
        HttpResponse<String> response = sendJson(inputs, url, "GET", null);
        return toRunRecord(inputs, response.body());
    }

    @Override
    public List<AzureDevOpsRepositoryDefinitionRecord> listRepositories(AzureDevOpsPipelineDiscoveryInputs inputs) {
        String url = repositoryCollectionApiUrl(inputs);
        HttpResponse<String> response = sendJson(inputs, url, "GET", null);
        return toRepositoryDefinitions(inputs, response.body());
    }

    @Override
    public List<AzureDevOpsBranchDefinitionRecord> listBranches(AzureDevOpsBranchDiscoveryInputs inputs) {
        String url = branchCollectionApiUrl(inputs);
        HttpResponse<String> response = sendJson(
            firstNonBlank(inputs == null ? "" : inputs.personalAccessToken(), properties.getAzureDevOps().getPersonalAccessToken()),
            url,
            "GET",
            null
        );
        return toBranchDefinitions(response.body());
    }

    @Override
    public List<AzureDevOpsPipelineDefinitionRecord> listPipelines(AzureDevOpsPipelineDiscoveryInputs inputs) {
        String url = pipelineCollectionApiUrl(inputs);
        HttpResponse<String> response = sendJson(inputs, url, "GET", null);
        return toPipelineDefinitions(inputs, response.body());
    }

    @Override
    public List<AzureDevOpsServiceConnectionDefinitionRecord> listServiceConnections(
        AzureDevOpsPipelineDiscoveryInputs inputs
    ) {
        String url = serviceConnectionCollectionApiUrl(inputs);
        HttpResponse<String> response = sendJson(inputs, url, "GET", null);
        return toServiceConnectionDefinitions(inputs, response.body());
    }

    private HttpResponse<String> sendJson(AzureDevOpsPipelineInputs inputs, String url, String method, Object payload) {
        return sendJson(
            firstNonBlank(inputs == null ? "" : inputs.personalAccessToken(), properties.getAzureDevOps().getPersonalAccessToken()),
            url,
            method,
            payload
        );
    }

    private HttpResponse<String> sendJson(AzureDevOpsPipelineDiscoveryInputs inputs, String url, String method, Object payload) {
        return sendJson(
            firstNonBlank(inputs == null ? "" : inputs.personalAccessToken(), properties.getAzureDevOps().getPersonalAccessToken()),
            url,
            method,
            payload
        );
    }

    private HttpResponse<String> sendJson(String personalAccessToken, String url, String method, Object payload) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", authorizationHeader(personalAccessToken))
                .timeout(Duration.ofMillis(Math.max(1_000L, properties.getAzureDevOps().getReadTimeoutMs())));

            if ("POST".equalsIgnoreCase(method)) {
                String body = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
                request.header("Content-Type", "application/json");
                request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                request.GET();
            }

            HttpResponse<String> response = httpClient.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AzureDevOpsClientException(
                    "Azure DevOps API request failed with HTTP " + response.statusCode() + ".",
                    response.statusCode(),
                    response.body()
                );
            }
            return response;
        } catch (AzureDevOpsClientException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AzureDevOpsClientException(
                "Azure DevOps API request failed: " + exception.getMessage(),
                0,
                ""
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AzureDevOpsClientException(
                "Azure DevOps API request interrupted: " + exception.getMessage(),
                0,
                ""
            );
        }
    }

    private List<AzureDevOpsProjectDefinitionRecord> toProjectDefinitions(
        String organization,
        String responseBody
    ) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<AzureDevOpsProjectDefinitionRecord> projects = new ArrayList<>();
            JsonNode values = root.path("value");
            if (values.isArray()) {
                for (JsonNode node : values) {
                    String id = normalize(text(node.path("id")));
                    if (id.isBlank()) {
                        continue;
                    }
                    String name = firstNonBlank(text(node.path("name")), "project-" + id);
                    String webUrl = firstNonBlank(
                        text(node.at("/_links/web/href")),
                        organizationUrl(organization) + "/" + encodePath(name)
                    );
                    projects.add(new AzureDevOpsProjectDefinitionRecord(id, name, webUrl));
                }
            }
            return projects;
        } catch (Exception exception) {
            throw new AzureDevOpsClientException(
                "Azure DevOps project list response parsing failed: " + exception.getMessage(),
                0,
                responseBody
            );
        }
    }

    private List<AzureDevOpsPipelineDefinitionRecord> toPipelineDefinitions(
        AzureDevOpsPipelineDiscoveryInputs inputs,
        String responseBody
    ) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<AzureDevOpsPipelineDefinitionRecord> pipelines = new ArrayList<>();
            JsonNode values = root.path("value");
            if (values.isArray()) {
                for (JsonNode node : values) {
                    String id = normalize(text(node.path("id")));
                    if (id.isBlank()) {
                        continue;
                    }
                    String name = firstNonBlank(text(node.path("name")), "pipeline-" + id);
                    String folder = normalize(text(node.path("folder")));
                    String webUrl = firstNonBlank(
                        text(node.at("/_links/web/href")),
                        organizationUrl(inputs.organization())
                            + "/" + encodePath(inputs.project())
                            + "/_build?definitionId=" + encodeQueryParam(id)
                    );
                    String apiUrl = firstNonBlank(
                        text(node.path("url")),
                        organizationUrl(inputs.organization())
                            + "/" + encodePath(inputs.project())
                            + "/_apis/pipelines/" + encodePath(id)
                            + "?api-version=" + encodeQueryParam(properties.getAzureDevOps().getApiVersion())
                    );
                    pipelines.add(new AzureDevOpsPipelineDefinitionRecord(id, name, folder, webUrl, apiUrl));
                }
            }
            return pipelines;
        } catch (Exception exception) {
            throw new AzureDevOpsClientException(
                "Azure DevOps pipeline list response parsing failed: " + exception.getMessage(),
                0,
                responseBody
            );
        }
    }

    private List<AzureDevOpsRepositoryDefinitionRecord> toRepositoryDefinitions(
        AzureDevOpsPipelineDiscoveryInputs inputs,
        String responseBody
    ) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<AzureDevOpsRepositoryDefinitionRecord> repositories = new ArrayList<>();
            JsonNode values = root.path("value");
            if (values.isArray()) {
                for (JsonNode node : values) {
                    String id = normalize(text(node.path("id")));
                    if (id.isBlank()) {
                        continue;
                    }
                    String name = firstNonBlank(text(node.path("name")), "repo-" + id);
                    String defaultBranch = normalize(text(node.path("defaultBranch")));
                    String remoteUrl = normalize(text(node.path("remoteUrl")));
                    String webUrl = firstNonBlank(
                        text(node.path("webUrl")),
                        organizationUrl(inputs.organization())
                            + "/" + encodePath(inputs.project())
                            + "/_git/" + encodePath(name)
                    );
                    repositories.add(
                        new AzureDevOpsRepositoryDefinitionRecord(id, name, defaultBranch, webUrl, remoteUrl)
                    );
                }
            }
            return repositories;
        } catch (Exception exception) {
            throw new AzureDevOpsClientException(
                "Azure DevOps repository list response parsing failed: " + exception.getMessage(),
                0,
                responseBody
            );
        }
    }

    private List<AzureDevOpsServiceConnectionDefinitionRecord> toServiceConnectionDefinitions(
        AzureDevOpsPipelineDiscoveryInputs inputs,
        String responseBody
    ) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<AzureDevOpsServiceConnectionDefinitionRecord> serviceConnections = new ArrayList<>();
            JsonNode values = root.path("value");
            if (values.isArray()) {
                for (JsonNode node : values) {
                    String id = normalize(text(node.path("id")));
                    if (id.isBlank()) {
                        continue;
                    }
                    String name = firstNonBlank(text(node.path("name")), "service-connection-" + id);
                    String type = normalize(text(node.path("type")));
                    String webUrl = firstNonBlank(
                        text(node.path("url")),
                        organizationUrl(inputs.organization())
                            + "/" + encodePath(inputs.project())
                            + "/_settings/adminservices?resourceId=" + encodeQueryParam(id)
                    );
                    serviceConnections.add(
                        new AzureDevOpsServiceConnectionDefinitionRecord(id, name, type, webUrl)
                    );
                }
            }
            return serviceConnections;
        } catch (Exception exception) {
            throw new AzureDevOpsClientException(
                "Azure DevOps service connection list response parsing failed: " + exception.getMessage(),
                0,
                responseBody
            );
        }
    }

    private List<AzureDevOpsBranchDefinitionRecord> toBranchDefinitions(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<AzureDevOpsBranchDefinitionRecord> branches = new ArrayList<>();
            JsonNode values = root.path("value");
            if (values.isArray()) {
                for (JsonNode node : values) {
                    String refName = normalize(text(node.path("name")));
                    String name = normalizeBranchName(refName);
                    if (name.isBlank()) {
                        continue;
                    }
                    branches.add(new AzureDevOpsBranchDefinitionRecord(name, refName));
                }
            }
            return branches;
        } catch (Exception exception) {
            throw new AzureDevOpsClientException(
                "Azure DevOps branch list response parsing failed: " + exception.getMessage(),
                0,
                responseBody
            );
        }
    }

    private AzureDevOpsPipelineRunRecord toRunRecord(AzureDevOpsPipelineInputs inputs, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String runId = normalize(text(root.path("id")));
            if (runId.isBlank()) {
                throw new AzureDevOpsClientException(
                    "Azure DevOps API response did not include a run id.",
                    0,
                    responseBody
                );
            }

            String runName = firstNonBlank(text(root.path("name")), runId);
            String webUrl = firstNonBlank(
                text(root.at("/_links/web/href")),
                buildRunWebUrl(inputs, runId)
            );
            String logsUrl = firstNonBlank(
                text(root.at("/_links/logs/href")),
                appendViewLogs(webUrl)
            );

            return new AzureDevOpsPipelineRunRecord(
                runId,
                runName,
                normalize(text(root.path("state"))),
                normalize(text(root.path("result"))),
                webUrl,
                logsUrl,
                firstNonBlank(text(root.path("url")), runItemApiUrl(inputs, runId))
            );
        } catch (AzureDevOpsClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AzureDevOpsClientException(
                "Azure DevOps API response parsing failed: " + exception.getMessage(),
                0,
                responseBody
            );
        }
    }

    private String authorizationHeader(String personalAccessToken) {
        String pat = firstNonBlank(personalAccessToken, properties.getAzureDevOps().getPersonalAccessToken());
        String token = Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private String runCollectionApiUrl(AzureDevOpsPipelineInputs inputs) {
        return organizationUrl(inputs.organization())
            + "/" + encodePath(inputs.project())
            + "/_apis/pipelines/" + encodePath(inputs.pipelineId())
            + "/runs?api-version=" + encodeQueryParam(properties.getAzureDevOps().getApiVersion());
    }

    private String runItemApiUrl(AzureDevOpsPipelineInputs inputs, String runId) {
        return organizationUrl(inputs.organization())
            + "/" + encodePath(inputs.project())
            + "/_apis/pipelines/" + encodePath(inputs.pipelineId())
            + "/runs/" + encodePath(runId)
            + "?api-version=" + encodeQueryParam(properties.getAzureDevOps().getApiVersion());
    }

    private String pipelineCollectionApiUrl(AzureDevOpsPipelineDiscoveryInputs inputs) {
        return organizationUrl(inputs.organization())
            + "/" + encodePath(inputs.project())
            + "/_apis/pipelines?api-version=" + encodeQueryParam(properties.getAzureDevOps().getApiVersion())
            + "&$top=200";
    }

    private String projectCollectionApiUrl(String organization) {
        return organizationUrl(organization)
            + "/_apis/projects?api-version=7.1-preview.4&$top=200";
    }

    private String repositoryCollectionApiUrl(AzureDevOpsPipelineDiscoveryInputs inputs) {
        return organizationUrl(inputs.organization())
            + "/" + encodePath(inputs.project())
            + "/_apis/git/repositories?api-version=7.1-preview.1";
    }

    private String branchCollectionApiUrl(AzureDevOpsBranchDiscoveryInputs inputs) {
        return organizationUrl(inputs.organization())
            + "/" + encodePath(inputs.project())
            + "/_apis/git/repositories/" + encodePath(firstNonBlank(inputs.repositoryId(), inputs.repository()))
            + "/refs?filter=heads/&api-version=7.1-preview.1&$top=200";
    }

    private String serviceConnectionCollectionApiUrl(AzureDevOpsPipelineDiscoveryInputs inputs) {
        return organizationUrl(inputs.organization())
            + "/" + encodePath(inputs.project())
            + "/_apis/serviceendpoint/endpoints?api-version=7.1-preview.4"
            + "&$top=200";
    }

    private String buildRunWebUrl(AzureDevOpsPipelineInputs inputs, String runId) {
        return organizationUrl(inputs.organization())
            + "/" + encodePath(inputs.project())
            + "/_build/results?buildId=" + encodeQueryParam(runId) + "&view=results";
    }

    private String organizationUrl(String organization) {
        return AzureDevOpsUrlNormalizer.normalizeOrganizationUrl(
            organization,
            properties.getAzureDevOps().getBaseUrl()
        );
    }

    private String branchRef(String branch) {
        String normalized = normalize(branch);
        if (normalized.isBlank()) {
            return "refs/heads/main";
        }
        if (normalized.startsWith("refs/")) {
            return normalized;
        }
        return "refs/heads/" + normalized;
    }

    private String appendViewLogs(String webUrl) {
        String normalized = normalize(webUrl);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.contains("view=logs")) {
            return normalized;
        }
        return normalized.contains("?")
            ? normalized + "&view=logs"
            : normalized + "?view=logs";
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

    private String encodePath(String value) {
        return URLEncoder.encode(normalize(value), StandardCharsets.UTF_8);
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(normalize(value), StandardCharsets.UTF_8);
    }

    private String text(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private String trimTrailingSlash(String value) {
        String normalized = normalize(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeBranchName(String refName) {
        String normalized = normalize(refName);
        if (normalized.startsWith("refs/heads/")) {
            return normalized.substring("refs/heads/".length());
        }
        return "";
    }
}
