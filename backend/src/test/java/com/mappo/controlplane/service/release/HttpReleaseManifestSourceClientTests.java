package com.mappo.controlplane.service.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mappo.controlplane.config.MappoProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpReleaseManifestSourceClientTests {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchGithubManifestUsesRawUrlWithoutToken() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        startServer(requestPath, authHeader, "[1]");

        MappoProperties properties = new MappoProperties();
        HttpReleaseManifestSourceClient client = new TestHttpReleaseManifestSourceClient(
            properties,
            HttpClient.newHttpClient(),
            URI.create(baseUrl()),
            URI.create(baseUrl())
        );

        String body = client.fetchGithubManifest("cvonderheid/mappo-managed-app", "releases/releases.manifest.json", "main");

        assertEquals("[1]", body);
        assertEquals("/cvonderheid/mappo-managed-app/main/releases/releases.manifest.json", requestPath.get());
        assertEquals(null, authHeader.get());
    }

    @Test
    void fetchGithubManifestUsesGithubApiWhenTokenConfigured() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        startServer(requestPath, authHeader, "[2]");

        MappoProperties properties = new MappoProperties();
        properties.setManagedAppReleaseGithubToken("test-token");
        HttpReleaseManifestSourceClient client = new TestHttpReleaseManifestSourceClient(
            properties,
            HttpClient.newHttpClient(),
            URI.create(baseUrl()),
            URI.create(baseUrl())
        );

        String body = client.fetchGithubManifest("cvonderheid/mappo-managed-app", "releases/releases.manifest.json", "main");

        assertEquals("[2]", body);
        assertEquals("/repos/cvonderheid/mappo-managed-app/contents/releases/releases.manifest.json?ref=main", requestPath.get());
        assertEquals("Bearer test-token", authHeader.get());
    }

    private void startServer(AtomicReference<String> requestPath, AtomicReference<String> authHeader, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> handle(exchange, requestPath, authHeader, responseBody));
        server.start();
    }

    private void handle(
        HttpExchange exchange,
        AtomicReference<String> requestPath,
        AtomicReference<String> authHeader,
        String responseBody
    ) throws IOException {
        requestPath.set(exchange.getRequestURI().toString());
        authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:%d".formatted(server.getAddress().getPort());
    }

    private static final class TestHttpReleaseManifestSourceClient extends HttpReleaseManifestSourceClient {

        private final URI apiBaseUri;
        private final URI rawBaseUri;

        private TestHttpReleaseManifestSourceClient(
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
