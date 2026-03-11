package com.mappo.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.api-docs.path=/api/v1/openapi.json"
    }
)
class MappoApplicationTests extends PostgresIntegrationTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

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
        MvcResult result = mockMvc.perform(get("/api/v1/events/stream"))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
    }

    private String fetchOpenApiJson() throws Exception {
        return mockMvc.perform(get("/api/v1/openapi.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }
}
