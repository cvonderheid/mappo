package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class HttpReleaseManifestSourceClient implements ReleaseManifestSourceClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public String fetchGithubManifest(String repo, String path, String ref) {
        String safePath = path.replaceFirst("^/+", "");
        String url = "https://raw.githubusercontent.com/%s/%s/%s".formatted(
            repo,
            encodeSegment(ref),
            safePath
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "managed-app release manifest fetch failed (HTTP %d)".formatted(response.statusCode())
                );
            }
            return response.body();
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "managed-app release manifest fetch failed: " + exception.getMessage());
        }
    }

    private String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
