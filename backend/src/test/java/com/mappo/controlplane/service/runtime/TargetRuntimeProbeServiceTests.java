package com.mappo.controlplane.service.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import com.mappo.controlplane.repository.TargetRuntimeProbeContextRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetRuntimeProbeServiceTests {

    @Mock
    private TargetRuntimeProbeContextRepository targetRuntimeProbeContextRepository;

    @Mock
    private TargetRuntimeProbeExecutionService targetRuntimeProbeExecutionService;

    @Mock
    private LiveUpdateService liveUpdateService;

    @Test
    void refreshRuntimeProbesPersistsProbeResults() {
        MappoProperties properties = new MappoProperties();
        properties.getRuntimeProbe().setEnabled(true);
        TargetRuntimeProbeService service = new TargetRuntimeProbeService(
            targetRuntimeProbeContextRepository,
            targetRuntimeProbeExecutionService,
            properties,
            liveUpdateService
        );
        TargetRuntimeProbeContextRecord target = new TargetRuntimeProbeContextRecord(
            "target-01",
            "azure-managed-app-deployment-stack",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "/subscriptions/22222222-2222-2222-2222-222222222222/resourceGroups/rg-target-01/providers/Microsoft.App/containerApps/ca-target-01",
            ProjectRuntimeHealthProviderType.azure_container_app_http,
            "/health",
            200,
            5000L,
            java.util.Map.of()
        );
        TargetRuntimeProbeRecord probe = new TargetRuntimeProbeRecord(
            "target-01",
            MappoRuntimeProbeStatus.healthy,
            OffsetDateTime.parse("2026-03-09T12:00:00Z"),
            "https://target-01.example.com",
            200,
            "Runtime responded with HTTP 200."
        );

        when(targetRuntimeProbeContextRepository.listRuntimeProbeContexts()).thenReturn(List.of(target));
        when(targetRuntimeProbeExecutionService.probeAndPersist(target)).thenReturn(java.util.Optional.of(probe));

        service.refreshRuntimeProbes();

        verify(targetRuntimeProbeExecutionService).probeAndPersist(target);
        verify(liveUpdateService).emitTargetsUpdated("azure-managed-app-deployment-stack");
    }

    @Test
    void refreshRuntimeProbesSkipsWhenClientIsNotConfigured() {
        MappoProperties properties = new MappoProperties();
        properties.getRuntimeProbe().setEnabled(true);
        TargetRuntimeProbeService service = new TargetRuntimeProbeService(
            targetRuntimeProbeContextRepository,
            targetRuntimeProbeExecutionService,
            properties,
            liveUpdateService
        );

        when(targetRuntimeProbeContextRepository.listRuntimeProbeContexts()).thenReturn(List.of());

        service.refreshRuntimeProbes();

        verify(targetRuntimeProbeContextRepository).listRuntimeProbeContexts();
        verify(targetRuntimeProbeExecutionService, never()).probeAndPersist(any());
    }
}
