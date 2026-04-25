package com.mappo.controlplane.integrations.github.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mappo.controlplane.config.MappoProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HttpGithubReleaseManifestSourceClientTests {

    private HttpClient httpClient;
    @SuppressWarnings("unchecked")
    private HttpResponse<String> response = mock(HttpResponse.class);

    @BeforeEach
    void setUp() throws Exception {
        httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    void fetchGithubManifestUsesRawUrlWithoutToken() throws Exception {
        MappoProperties properties = new MappoProperties();
        HttpGithubReleaseManifestSourceClient client = new TestHttpGithubReleaseManifestSourceClient(
            properties,
            httpClient,
            URI.create("https://api.github.example"),
            URI.create("https://raw.githubusercontent.example")
        );
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[1]");

        String body = client.fetchManifest("example-org/mappo-release-catalog", "releases/releases.manifest.json", "main");
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        assertEquals("[1]", body);
        assertEquals(
            URI.create("https://raw.githubusercontent.example/example-org/mappo-release-catalog/main/releases/releases.manifest.json"),
            requestCaptor.getValue().uri()
        );
        assertEquals(null, requestCaptor.getValue().headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void fetchGithubManifestUsesGithubApiWhenTokenConfigured() throws Exception {
        MappoProperties properties = new MappoProperties();
        properties.getManagedAppRelease().setGithubToken("test-token");
        HttpGithubReleaseManifestSourceClient client = new TestHttpGithubReleaseManifestSourceClient(
            properties,
            httpClient,
            URI.create("https://api.github.example"),
            URI.create("https://raw.githubusercontent.example")
        );
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[2]");

        String body = client.fetchManifest("example-org/mappo-release-catalog", "releases/releases.manifest.json", "main");
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        assertEquals("[2]", body);
        assertEquals(
            URI.create("https://api.github.example/repos/example-org/mappo-release-catalog/contents/releases/releases.manifest.json?ref=main"),
            requestCaptor.getValue().uri()
        );
        assertEquals("Bearer test-token", requestCaptor.getValue().headers().firstValue("Authorization").orElse(null));
    }

    private static final class TestHttpGithubReleaseManifestSourceClient extends HttpGithubReleaseManifestSourceClient {

        private final URI apiBaseUri;
        private final URI rawBaseUri;

        private TestHttpGithubReleaseManifestSourceClient(
            MappoProperties properties,
            HttpClient httpClient,
            URI apiBaseUri,
            URI rawBaseUri
        ) {
            super(properties, httpClient);
            this.apiBaseUri = apiBaseUri;
            this.rawBaseUri = rawBaseUri;
        }

        @Override
        protected URI githubApiBaseUri() {
            return apiBaseUri;
        }

        @Override
        protected URI githubRawBaseUri() {
            return rawBaseUri;
        }
    }
}
