package com.mappo.controlplane.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpEndpointRuntimeHealthProviderTests {

    @Mock
    private HttpRuntimeProbeClient httpRuntimeProbeClient;

    @Test
    void supportsHttpEndpointTargetsWithConfiguredBaseUrl() {
        HttpEndpointRuntimeHealthProvider provider = new HttpEndpointRuntimeHealthProvider(httpRuntimeProbeClient);

        assertThat(provider.supports(httpTarget(Map.of("runtimeBaseUrl", "https://demo.example.com")))).isTrue();
        assertThat(provider.supports(httpTarget(Map.of()))).isFalse();
    }

    @Test
    void probeBuildsRequestUsingOverrides() {
        HttpEndpointRuntimeHealthProvider provider = new HttpEndpointRuntimeHealthProvider(httpRuntimeProbeClient);
        TargetRuntimeProbeContextRecord target = httpTarget(Map.of(
            "runtimeBaseUrl", "https://demo.example.com/base/",
            "runtimeHealthPath", "/readyz",
            "runtimeExpectedStatus", "204",
            "runtimeHealthTimeoutMs", "9000"
        ));
        TargetRuntimeProbeRecord probe = new TargetRuntimeProbeRecord(
            "target-01",
            MappoRuntimeProbeStatus.healthy,
            OffsetDateTime.now(ZoneOffset.UTC),
            "https://demo.example.com/readyz",
            204,
            "ok"
        );
        when(httpRuntimeProbeClient.probe(org.mockito.ArgumentMatchers.eq("target-01"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(probe);

        TargetRuntimeProbeRecord result = provider.probe(target);

        assertThat(result).isEqualTo(probe);
        ArgumentCaptor<HttpRuntimeProbeRequest> requestCaptor = forClass(HttpRuntimeProbeRequest.class);
        verify(httpRuntimeProbeClient).probe(org.mockito.ArgumentMatchers.eq("target-01"), org.mockito.ArgumentMatchers.any(), requestCaptor.capture());
        HttpRuntimeProbeRequest request = requestCaptor.getValue();
        assertThat(request.primaryUrl()).isEqualTo("https://demo.example.com/readyz");
        assertThat(request.expectedStatus()).isEqualTo(204);
        assertThat(request.timeoutMs()).isEqualTo(9000L);
    }

    private TargetRuntimeProbeContextRecord httpTarget(Map<String, String> executionConfig) {
        return new TargetRuntimeProbeContextRecord(
            "target-01",
            "azure-appservice-ado-pipeline",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "",
            com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType.http_endpoint,
            "/health",
            200,
            5000L,
            executionConfig
        );
    }
}
