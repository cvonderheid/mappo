package com.mappo.controlplane;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class MappoApplicationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void openApiIsVersionedUnderApiPrefix() throws Exception {
        mockMvc.perform(get("/api/v1/openapi.json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").isNotEmpty())
            .andExpect(jsonPath("$.paths").exists());
    }

    @Test
    void openApiExportsPaginatedQueryParametersForOperatorCollections() throws Exception {
        mockMvc.perform(get("/api/v1/openapi.json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/runs'].get.parameters[?(@.name == 'page')]").isNotEmpty())
            .andExpect(jsonPath("$.paths['/api/v1/runs'].get.parameters[?(@.name == 'size')]").isNotEmpty())
            .andExpect(jsonPath("$.paths['/api/v1/runs'].get.parameters[?(@.name == 'status')]").isNotEmpty())
            .andExpect(jsonPath("$.paths['/api/v1/targets/page'].get.parameters[?(@.name == 'page')]").isNotEmpty())
            .andExpect(jsonPath("$.paths['/api/v1/targets/page'].get.parameters[?(@.name == 'runtimeStatus')]").isNotEmpty())
            .andExpect(jsonPath("$.components.schemas.TargetRecord.properties.runtimeStatus").exists())
            .andExpect(jsonPath("$.components.schemas.TargetRecord.properties.runtimeCheckedAt").exists())
            .andExpect(jsonPath("$.components.schemas.TargetRecord.properties.runtimeSummary").exists())
            .andExpect(jsonPath("$.components.schemas.PageMetadataRecord.required").isArray())
            .andExpect(jsonPath("$.components.schemas.TargetPageRecord.required").isArray())
            .andExpect(jsonPath("$.components.schemas.RunSummaryPageRecord.required").isArray())
            .andExpect(jsonPath("$.components.schemas.PageMetadataRecord.required", hasItem("page")))
            .andExpect(jsonPath("$.components.schemas.TargetPageRecord.required", hasItem("items")))
            .andExpect(
                content().string(
                    containsString("\"name\":\"status\"")
                )
            )
            .andExpect(
                content().string(
                    containsString("\"enum\":[\"running\",\"succeeded\",\"failed\",\"partial\",\"halted\"]")
                )
            )
            .andExpect(
                content().string(
                    containsString("\"name\":\"runtimeStatus\"")
                )
            )
            .andExpect(
                content().string(
                    containsString("\"enum\":[\"unknown\",\"healthy\",\"unhealthy\",\"unreachable\"]")
                )
            )
            .andExpect(
                jsonPath("$.paths['/api/v1/admin/onboarding/registrations'].get.parameters[?(@.name == 'targetId')]")
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.paths['/api/v1/admin/onboarding/events'].get.parameters[?(@.name == 'eventId')]")
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.paths['/api/v1/admin/onboarding/forwarder-logs/page'].get.parameters[?(@.name == 'level')]")
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.paths['/api/v1/admin/releases/webhook-deliveries'].get.parameters[?(@.name == 'deliveryId')]")
                    .isNotEmpty()
            )
            .andExpect(jsonPath("$.paths['/api/v1/targets'].get.deprecated").value(true))
            .andExpect(jsonPath("$.paths['/api/v1/admin/onboarding'].get.deprecated").value(true))
            .andExpect(jsonPath("$.paths['/api/v1/admin/onboarding/forwarder-logs'].get.deprecated").value(true));
    }

    @Test
    void liveEventsStreamStartsAsync() throws Exception {
        mockMvc.perform(get("/api/v1/events/stream"))
            .andExpect(request().asyncStarted());
    }
}
