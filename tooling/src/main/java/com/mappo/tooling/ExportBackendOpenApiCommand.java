package com.mappo.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExportBackendOpenApiCommand {

    private final ObjectMapper objectMapper = new ObjectMapper();

    int run(List<String> rawArgs) {
        Arguments args = new Arguments(rawArgs);
        String url = null;
        Path output = null;
        int timeoutSeconds = 60;
        double pollIntervalSeconds = 1.0d;

        while (args.hasNext()) {
            String arg = args.next();
            switch (arg) {
                case "--url" -> url = args.nextRequired("--url");
                case "--output" -> output = Path.of(args.nextRequired("--output"));
                case "--timeout-seconds" -> timeoutSeconds = Integer.parseInt(args.nextRequired("--timeout-seconds"));
                case "--poll-interval-seconds" ->
                    pollIntervalSeconds = Double.parseDouble(args.nextRequired("--poll-interval-seconds"));
                case "-h", "--help" -> {
                    printUsage();
                    return 0;
                }
                default -> throw new ToolingException("export-backend-openapi: unknown argument: " + arg, 2);
            }
        }

        if (url == null || output == null) {
            throw new ToolingException("export-backend-openapi: --url and --output are required", 2);
        }

        Map<String, Object> payload = fetchOpenApi(url, timeoutSeconds, pollIntervalSeconds);
        try {
            String serialized = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload) + System.lineSeparator();
            FileSupport.writeText(output, serialized);
        } catch (JsonProcessingException exception) {
            throw new ToolingException("export-backend-openapi: failed serializing payload: " + exception.getMessage(), 1);
        }
        System.out.println("export-backend-openapi: wrote " + output.toAbsolutePath().normalize());
        return 0;
    }

    private Map<String, Object> fetchOpenApi(String url, int timeoutSeconds, double pollIntervalSeconds) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        String lastError = "unknown";

        while (Instant.now().isBefore(deadline)) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<LinkedHashMap<String, Object>>() {
                    });
                    if (payload.get("openapi") != null) {
                        return payload;
                    }
                    lastError = "endpoint responded but did not return an OpenAPI document";
                } else {
                    lastError = "HTTP " + response.statusCode();
                }
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            }

            try {
                Thread.sleep((long) (pollIntervalSeconds * 1000));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ToolingException("export-backend-openapi: interrupted while waiting for endpoint", 1);
            }
        }

        throw new ToolingException(
            "export-backend-openapi: timed out waiting for OpenAPI endpoint '" + url + "'. Last error: " + lastError,
            1
        );
    }

    private void printUsage() {
        System.out.println("""
            usage: export-backend-openapi --url <url> --output <path>
                   [--timeout-seconds <int>] [--poll-interval-seconds <float>]
            """);
    }
}
