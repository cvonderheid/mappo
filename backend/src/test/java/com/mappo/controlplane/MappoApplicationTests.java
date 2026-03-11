package com.mappo.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.api-docs.path=/api/v1/openapi.json"
    }
)
class MappoApplicationTests extends PostgresIntegrationTestBase {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void openApiIsVersionedUnderApiPrefix() throws Exception {
        DocumentContext openApi = JsonPath.parse(fetchOpenApiJson());

        assertThat(openApi.<String>read("$.openapi")).isNotBlank();
        assertThat(openApi.<Map<String, Object>>read("$.paths")).isNotEmpty();
    }

    @Test
    void openApiExportsPaginatedQueryParametersForOperatorCollections() throws Exception {
        String openApiJson = fetchOpenApiJson();
        DocumentContext json = JsonPath.parse(openApiJson);
        Map<String, Object> paths = json.read("$.paths");

        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/runs'].get.parameters[?(@.name == 'page')]")).isNotEmpty();
        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/runs'].get.parameters[?(@.name == 'size')]")).isNotEmpty();
        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/runs'].get.parameters[?(@.name == 'status')]")).isNotEmpty();
        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/targets/page'].get.parameters[?(@.name == 'page')]")).isNotEmpty();
        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/targets/page'].get.parameters[?(@.name == 'runtimeStatus')]")).isNotEmpty();
        assertThat(json.<Object>read("$.components.schemas.TargetRecord.properties.runtimeStatus")).isNotNull();
        assertThat(json.<Object>read("$.components.schemas.TargetRecord.properties.runtimeCheckedAt")).isNotNull();
        assertThat(json.<Object>read("$.components.schemas.TargetRecord.properties.runtimeSummary")).isNotNull();
        assertThat(json.<java.util.List<String>>read("$.components.schemas.PageMetadataRecord.required")).contains("page");
        assertThat(json.<java.util.List<String>>read("$.components.schemas.TargetPageRecord.required")).contains("items");
        assertThat(openApiJson).contains("\"name\":\"status\"");
        assertThat(openApiJson).contains("\"enum\":[\"running\",\"succeeded\",\"failed\",\"partial\",\"halted\"]");
        assertThat(openApiJson).contains("\"name\":\"runtimeStatus\"");
        assertThat(openApiJson).contains("\"enum\":[\"unknown\",\"healthy\",\"unhealthy\",\"unreachable\"]");
        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/admin/onboarding/registrations'].get.parameters[?(@.name == 'targetId')]"))
            .isNotEmpty();
        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/admin/onboarding/events'].get.parameters[?(@.name == 'eventId')]"))
            .isNotEmpty();
        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/admin/onboarding/forwarder-logs/page'].get.parameters[?(@.name == 'level')]"))
            .isNotEmpty();
        assertThat(json.<java.util.List<?>>read("$.paths['/api/v1/admin/releases/webhook-deliveries'].get.parameters[?(@.name == 'deliveryId')]"))
            .isNotEmpty();
        assertThat(paths).doesNotContainKeys("/api/v1/targets", "/api/v1/admin/onboarding");
        assertThat(openApiJson).doesNotContain("\"/api/v1/admin/onboarding/forwarder-logs\":{\"get\"");
    }

    @Test
    void liveEventsStreamStartsAsync() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl() + "/api/v1/events/stream")
            .openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);

        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            assertThat(connection.getContentType()).contains("text/event-stream");
            try (InputStream ignored = connection.getInputStream()) {
                // Opening the stream is sufficient to prove the endpoint is live.
            }
        } finally {
            connection.disconnect();
        }
    }

    private String fetchOpenApiJson() {
        return RestClient.create()
            .get()
            .uri(URI.create(baseUrl() + "/api/v1/openapi.json"))
            .retrieve()
            .body(String.class);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}
