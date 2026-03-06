package com.mappo.forwarder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class MarketplaceForwarderHandlerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void rejectsInvalidJson() throws Exception {
        RecordingBackendPoster backendPoster = new RecordingBackendPoster();
        MarketplaceForwarderHandler handler = handler(backendPoster);

        ForwarderResponse response = handler.handle("{", Map.of());

        assertEquals(400, response.statusCode());
        assertEquals("Invalid JSON payload.", responseMap(response).get("detail"));
        assertEquals(1, backendPoster.requests.size());
        assertEquals("/api/v1/admin/onboarding/forwarder-logs", backendPoster.requests.getFirst().endpointSuffix());
    }

    @Test
    void normalizesWrapperPayloadToCamelCaseOnboardingRequest() throws Exception {
        RecordingBackendPoster backendPoster = new RecordingBackendPoster();
        MarketplaceForwarderHandler handler = handler(backendPoster);

        ForwarderResponse response = handler.handle(
            """
            {
              "id": "evt-123",
              "event_type": "subscription_purchased",
              "mappo_target": {
                "tenant_id": "11111111-1111-1111-1111-111111111111",
                "subscription_id": "22222222-2222-2222-2222-222222222222",
                "target_id": "target-01",
                "display_name": "Target 01",
                "customer_name": "Acme",
                "container_app_resource_id": "/subscriptions/2222/resourceGroups/rg-demo/providers/Microsoft.App/containerApps/ca-demo",
                "tags": {
                  "ring": "canary"
                },
                "metadata": {
                  "owner": "demo"
                }
              }
            }
            """,
            Map.of("x-ms-request-id", "req-123")
        );

        assertEquals(202, response.statusCode());
        assertEquals(1, backendPoster.requests.size());

        RecordedRequest request = backendPoster.requests.getFirst();
        assertEquals("/api/v1/admin/onboarding/events", request.endpointSuffix());
        assertEquals("token-123", request.headers().get("x-mappo-ingest-token"));
        assertEquals("application/json", request.headers().get("Content-Type"));

        Map<String, Object> payload = request.payload();
        assertEquals("evt-123", payload.get("eventId"));
        assertEquals("subscription_purchased", payload.get("eventType"));
        assertEquals("11111111-1111-1111-1111-111111111111", payload.get("tenantId"));
        assertEquals("22222222-2222-2222-2222-222222222222", payload.get("subscriptionId"));
        assertEquals("target-01", payload.get("targetId"));
        assertEquals("Target 01", payload.get("displayName"));
        assertEquals("Acme", payload.get("customerName"));
        assertEquals("registered", payload.get("healthStatus"));
        assertEquals("unknown", payload.get("lastDeployedRelease"));

        @SuppressWarnings("unchecked")
        Map<String, String> tags = (Map<String, String>) payload.get("tags");
        assertEquals(Map.of("ring", "canary"), tags);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        assertEquals("demo", metadata.get("owner"));
        assertEquals("function-marketplace-forwarder", metadata.get("source"));
        assertEquals("evt-123", metadata.get("marketplacePayloadId"));
    }

    @Test
    void emitsForwarderLogWhenBackendRejectsEvent() throws Exception {
        RecordingBackendPoster backendPoster = new RecordingBackendPoster();
        backendPoster.responses.add(new HttpPostResult(422, "{\"detail\":\"invalid\"}"));
        backendPoster.responses.add(new HttpPostResult(202, "{\"applied\":true}"));
        MarketplaceForwarderHandler handler = handler(backendPoster);

        ForwarderResponse response = handler.handle(
            """
            {
              "eventId": "evt-200",
              "eventType": "subscription_purchased",
              "tenantId": "11111111-1111-1111-1111-111111111111",
              "subscriptionId": "22222222-2222-2222-2222-222222222222",
              "containerAppResourceId": "/subscriptions/2222/resourceGroups/rg-demo/providers/Microsoft.App/containerApps/ca-demo"
            }
            """,
            Map.of("x-request-id", "req-200")
        );

        assertEquals(422, response.statusCode());
        assertEquals(2, backendPoster.requests.size());
        assertEquals("/api/v1/admin/onboarding/events", backendPoster.requests.get(0).endpointSuffix());
        assertEquals("/api/v1/admin/onboarding/forwarder-logs", backendPoster.requests.get(1).endpointSuffix());

        Map<String, Object> logPayload = backendPoster.requests.get(1).payload();
        assertEquals("error", logPayload.get("level"));
        assertEquals("evt-200", logPayload.get("eventId"));
        assertEquals("subscription_purchased", logPayload.get("eventType"));
        assertEquals("req-200", logPayload.get("forwarderRequestId"));
        assertEquals(422, logPayload.get("backendStatusCode"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) logPayload.get("details");
        assertEquals("{\"detail\":\"invalid\"}", details.get("backendResponse"));
    }

    @Test
    void returnsBadGatewayWhenBackendCallThrows() throws Exception {
        RecordingBackendPoster backendPoster = new RecordingBackendPoster();
        backendPoster.throwOnRequest = true;
        MarketplaceForwarderHandler handler = handler(backendPoster);

        ForwarderResponse response = handler.handle(
            """
            {
              "eventId": "evt-500",
              "eventType": "subscription_purchased",
              "tenantId": "11111111-1111-1111-1111-111111111111",
              "subscriptionId": "22222222-2222-2222-2222-222222222222",
              "containerAppResourceId": "/subscriptions/2222/resourceGroups/rg-demo/providers/Microsoft.App/containerApps/ca-demo"
            }
            """,
            Map.of()
        );

        assertEquals(502, response.statusCode());
        assertTrue(String.valueOf(responseMap(response).get("detail")).contains("Forwarding failure"));
        assertTrue(backendPoster.requests.isEmpty());
    }

    private MarketplaceForwarderHandler handler(RecordingBackendPoster backendPoster) {
        Logger logger = Logger.getLogger("test");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        return new MarketplaceForwarderHandler(
            backendPoster,
            Map.of(
                "MAPPO_API_BASE_URL", "https://mappo.example",
                "MAPPO_INGEST_TOKEN", "token-123",
                "WEBSITE_SITE_NAME", "fa-mappo-test"
            ),
            logger
        );
    }

    private Map<String, Object> responseMap(ForwarderResponse response) throws Exception {
        return OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
    }

    private record RecordedRequest(String endpoint, Map<String, Object> payload, Map<String, String> headers) {
        private String endpointSuffix() {
            return endpoint.replace("https://mappo.example", "");
        }
    }

    private static final class RecordingBackendPoster implements BackendPoster {

        private final List<RecordedRequest> requests = new ArrayList<>();
        private final List<HttpPostResult> responses = new ArrayList<>();
        private boolean throwOnRequest;

        @Override
        public HttpPostResult postJson(String endpoint, Object payload, double timeoutSeconds, Map<String, String> headers) {
            if (throwOnRequest) {
                throw new IllegalStateException("boom");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> castPayload = payload instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : Map.of("payload", String.valueOf(payload));
            requests.add(new RecordedRequest(endpoint, castPayload, new LinkedHashMap<>(headers)));

            if (!responses.isEmpty()) {
                return responses.removeFirst();
            }
            return new HttpPostResult(202, "{\"applied\":true}");
        }
    }
}
