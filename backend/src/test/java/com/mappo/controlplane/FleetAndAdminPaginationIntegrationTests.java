package com.mappo.controlplane;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import com.mappo.controlplane.model.command.ReleaseWebhookDeliveryCommand;
import com.mappo.controlplane.persistence.release.ReleaseWebhookRepository;
import com.mappo.controlplane.persistence.target.TargetCommandRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FleetAndAdminPaginationIntegrationTests extends PostgresIntegrationTestBase {

    private static final String MANAGED_APP_PROJECT_ID = "azure-managed-app-deployment-stack";
    private static final String ADO_PROJECT_ID = "azure-appservice-ado-pipeline";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ReleaseWebhookRepository releaseWebhookRepository;

    @Autowired
    private TargetCommandRepository targetCommandRepository;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void fleetAndAdminEndpointsReturnPagedData() throws Exception {
        registerTarget("target-fleet-01", "11111111-1111-1111-1111-111111111111", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "canary", "centralus", "gold");
        registerTarget("target-fleet-02", "22222222-2222-2222-2222-222222222222", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "prod", "eastus", "gold");
        registerTarget("target-fleet-03", "33333333-3333-3333-3333-333333333333", "cccccccc-cccc-cccc-cccc-cccccccccccc", "prod", "westus2", "silver");
        targetCommandRepository.upsertRuntimeProbe(new TargetRuntimeProbeRecord(
            "target-fleet-01",
            MappoRuntimeProbeStatus.healthy,
            OffsetDateTime.parse("2026-03-09T10:00:00Z"),
            "https://demo-target-01.example.com",
            200,
            "Runtime responded with HTTP 200."
        ));
        targetCommandRepository.upsertRuntimeProbe(new TargetRuntimeProbeRecord(
            "target-fleet-02",
            MappoRuntimeProbeStatus.unreachable,
            OffsetDateTime.parse("2026-03-09T10:05:00Z"),
            "https://demo-target-02.example.com",
            null,
            "Runtime probe failed: connection refused."
        ));

        ingestForwarderLog("log-fleet-001", "error", "target-fleet-01");
        ingestForwarderLog("log-fleet-002", "warning", "target-fleet-02");

        releaseWebhookRepository.saveDelivery(new ReleaseWebhookDeliveryCommand(
            "wh-001",
            "gh-delivery-001",
            "push",
            "example-org/mappo-release-catalog",
            "refs/heads/main",
            "releases/releases.manifest.json",
            MappoReleaseWebhookStatus.applied,
            "created one release",
            java.util.List.of("releases/releases.manifest.json"),
            4,
            1,
            3,
            0,
            java.util.List.of("rel-123"),
            java.util.List.of(MANAGED_APP_PROJECT_ID),
            OffsetDateTime.now(ZoneOffset.UTC)
        ));
        releaseWebhookRepository.saveDelivery(new ReleaseWebhookDeliveryCommand(
            "wh-002",
            "gh-delivery-002",
            "push",
            "example-org/mappo-release-catalog",
            "refs/heads/main",
            "releases/releases.manifest.json",
            MappoReleaseWebhookStatus.skipped,
            "no new releases",
            java.util.List.of("README.md"),
            4,
            0,
            4,
            0,
            java.util.List.of(),
            java.util.List.of(ADO_PROJECT_ID),
            OffsetDateTime.now(ZoneOffset.UTC)
        ));

        mockMvc.perform(get("/api/v1/targets/page?page=0&size=1&ring=prod"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.page.totalItems").value(2))
            .andExpect(jsonPath("$.page.totalPages").value(2));

        mockMvc.perform(get("/api/v1/targets/page").param("targetId", "target-fleet-03"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("target-fleet-03"))
            .andExpect(jsonPath("$.items[0].runtimeStatus").doesNotExist());

        MvcResult unreachableTargetsResult = mockMvc.perform(get("/api/v1/targets/page").param("runtimeStatus", "unreachable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("target-fleet-02"))
            .andExpect(jsonPath("$.items[0].runtimeStatus").value("unreachable"))
            .andReturn();

        String runtimeCheckedAt = objectMapper.readTree(unreachableTargetsResult.getResponse().getContentAsByteArray())
            .path("items")
            .path(0)
            .path("runtimeCheckedAt")
            .asText();
        assertThat(OffsetDateTime.parse(runtimeCheckedAt).toInstant())
            .isEqualTo(OffsetDateTime.parse("2026-03-09T10:05:00Z").toInstant());

        mockMvc.perform(get("/api/v1/admin/onboarding/registrations?page=0&size=1&tier=gold"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.page.totalItems").value(2));

        mockMvc.perform(get("/api/v1/admin/onboarding/events?page=0&size=2&status=applied"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.page.totalItems").value(3));

        mockMvc.perform(get("/api/v1/admin/onboarding/forwarder-logs/page").param("level", "error"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].logId").value("log-fleet-001"));

        mockMvc.perform(get("/api/v1/admin/releases/webhook-deliveries").param("status", "skipped"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].externalDeliveryId").value("gh-delivery-002"));

        mockMvc.perform(get("/api/v1/admin/releases/webhook-deliveries").param("projectId", MANAGED_APP_PROJECT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].externalDeliveryId").value("gh-delivery-001"));
    }

    private void registerTarget(
        String targetId,
        String tenantId,
        String subscriptionId,
        String ring,
        String region,
        String tier
    ) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-" + targetId);
        event.put("eventType", "subscription_purchased");
        event.put("tenantId", tenantId);
        event.put("subscriptionId", subscriptionId);
        event.put("targetId", targetId);
        event.put("displayName", targetId);
        event.put("customerName", "Demo Customer " + targetId);
        event.put(
            "containerAppResourceId",
            "/subscriptions/" + subscriptionId + "/resourceGroups/rg-" + targetId + "/providers/Microsoft.App/containerApps/ca-" + targetId
        );
        event.put("managedResourceGroupId", "/subscriptions/" + subscriptionId + "/resourceGroups/rg-" + targetId);
        event.put("tags", Map.of("ring", ring, "region", region, "tier", tier, "environment", "prod"));

        mockMvc.perform(post("/api/v1/admin/onboarding/events")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(event)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"));
    }

    private void ingestForwarderLog(String logId, String level, String targetId) throws Exception {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("logId", logId);
        log.put("level", level);
        log.put("message", "log for " + targetId);
        log.put("targetId", targetId);
        log.put("eventId", "evt-" + targetId);
        log.put("eventType", "subscription_purchased");

        mockMvc.perform(post("/api/v1/admin/onboarding/forwarder-logs")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(log)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("applied"));
    }
}
