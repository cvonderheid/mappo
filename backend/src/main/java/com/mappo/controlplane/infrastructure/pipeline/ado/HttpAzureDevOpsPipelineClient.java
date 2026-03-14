package com.mappo.controlplane.infrastructure.pipeline.ado;

import com.mappo.controlplane.config.MappoProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
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
        HttpResponse<String> response = sendJson(url, "POST", body);
        return toRunRecord(inputs, response.body());
    }

    @Override
    public AzureDevOpsPipelineRunRecord getRun(AzureDevOpsPipelineInputs inputs, String runId) {
        String url = runItemApiUrl(inputs, normalize(runId));
        HttpResponse<String> response = sendJson(url, "GET", null);
        return toRunRecord(inputs, response.body());
    }

    private HttpResponse<String> sendJson(String url, String method, Object payload) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", authorizationHeader())
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

    private String authorizationHeader() {
        String pat = normalize(properties.getAzureDevOps().getPersonalAccessToken());
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

    private String buildRunWebUrl(AzureDevOpsPipelineInputs inputs, String runId) {
        return organizationUrl(inputs.organization())
            + "/" + encodePath(inputs.project())
            + "/_build/results?buildId=" + encodeQueryParam(runId) + "&view=results";
    }

    private String organizationUrl(String organization) {
        String normalizedOrganization = normalize(organization);
        if (normalizedOrganization.startsWith("https://") || normalizedOrganization.startsWith("http://")) {
            return trimTrailingSlash(normalizedOrganization);
        }
        String baseUrl = normalize(properties.getAzureDevOps().getBaseUrl());
        String resolvedBase = baseUrl.isBlank() ? "https://dev.azure.com" : trimTrailingSlash(baseUrl);
        return resolvedBase + "/" + encodePath(normalizedOrganization);
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
}
