package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.config.MappoProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class HttpReleaseManifestSourceClient implements ReleaseManifestSourceClient {

    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String GITHUB_RAW_ACCEPT = "application/vnd.github.raw";

    private final MappoProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public HttpReleaseManifestSourceClient(MappoProperties properties) {
        this(
            properties,
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        );
    }

    HttpReleaseManifestSourceClient(MappoProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public String fetchGithubManifest(String repo, String path, String ref) {
        String safePath = path.replaceFirst("^/+", "");
        HttpRequest request = buildRequest(repo, safePath, ref);
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

    private HttpRequest buildRequest(String repo, String safePath, String ref) {
        String token = normalize(properties.getManagedAppReleaseGithubToken());
        if (token.isBlank()) {
            String url = githubRawBaseUri().toString() + "/%s/%s/%s".formatted(
                repo,
                encodeSegment(ref),
                safePath
            );
            return HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build();
        }

        String[] repoParts = repo.split("/", 2);
        if (repoParts.length != 2 || repoParts[0].isBlank() || repoParts[1].isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "managed-app release repo must be in 'owner/name' format");
        }

        String url = githubApiBaseUri().toString() + "/repos/%s/%s/contents/%s?ref=%s".formatted(
            encodeSegment(repoParts[0]),
            encodeSegment(repoParts[1]),
            encodePath(safePath),
            encodeSegment(ref)
        );
        return HttpRequest.newBuilder(URI.create(url))
            .header("Accept", GITHUB_RAW_ACCEPT)
            .header("Authorization", "Bearer " + token)
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", "mappo-control-plane")
            .GET()
            .build();
    }

    private String encodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < segments.length; index += 1) {
            if (index > 0) {
                builder.append('/');
            }
            builder.append(encodeSegment(segments[index]));
        }
        return builder.toString();
    }

    private String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    protected URI githubApiBaseUri() {
        return URI.create("https://api.github.com");
    }

    protected URI githubRawBaseUri() {
        return URI.create("https://raw.githubusercontent.com");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
