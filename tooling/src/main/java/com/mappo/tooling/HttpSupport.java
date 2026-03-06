package com.mappo.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class HttpSupport {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    HttpSupport(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = objectMapper;
    }

    HttpResult get(String url, Map<String, String> headers, int timeoutSeconds) {
        return request("GET", url, headers, null, timeoutSeconds);
    }

    HttpResult postJson(String url, Object payload, Map<String, String> headers, int timeoutSeconds) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            Map<String, String> effectiveHeaders = new LinkedHashMap<>(headers);
            effectiveHeaders.putIfAbsent("Content-Type", "application/json");
            return request("POST", url, effectiveHeaders, body, timeoutSeconds);
        } catch (JsonProcessingException exception) {
            throw new ToolingException("failed serializing JSON payload: " + exception.getMessage(), 1);
        }
    }

    HttpResult request(String method, String url, Map<String, String> headers, String body, int timeoutSeconds) {
        HttpRequest.BodyPublisher publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .method(method, publisher);
        headers.forEach(builder::header);

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), response.body());
        } catch (IOException exception) {
            throw new ToolingException("HTTP " + method + " " + url + " failed: " + exception.getMessage(), 1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolingException("HTTP " + method + " " + url + " was interrupted", 1);
        }
    }

    static String basicAuthToken(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    record HttpResult(int statusCode, String body) {
        boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
