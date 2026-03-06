package com.mappo.forwarder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class JavaHttpBackendPoster implements BackendPoster {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public HttpPostResult postJson(String endpoint, Object payload, double timeoutSeconds, Map<String, String> headers) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize payload", exception);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofMillis((long) (timeoutSeconds * 1000)));

        Map<String, String> effectiveHeaders = new LinkedHashMap<>(headers);
        effectiveHeaders.forEach(builder::header);

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpPostResult(response.statusCode(), response.body());
        } catch (IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during HTTP request", exception);
        }
    }
}
