package com.mappo.controlplane.api;

import com.mappo.controlplane.model.ForwarderLogRecord;
import com.mappo.controlplane.model.OnboardingSnapshotRecord;
import com.mappo.controlplane.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Compatibility", description = "Deprecated snapshot endpoints retained for compatibility.")
public class AdminCompatibilityController {

    private final AdminService adminService;

    @GetMapping("/onboarding")
    @Deprecated
    @Operation(
        summary = "Get onboarding snapshot",
        description = "Compatibility snapshot endpoint for the Admin shell. Prefer the paginated onboarding/admin collection endpoints for operator tables.",
        deprecated = true
    )
    public OnboardingSnapshotRecord onboardingSnapshot(
        @RequestParam(value = "event_limit", defaultValue = "50") int eventLimit
    ) {
        return adminService.getOnboardingSnapshot(eventLimit);
    }

    @GetMapping("/onboarding/forwarder-logs")
    @Deprecated
    @Operation(
        summary = "List recent forwarder logs snapshot",
        description = "Compatibility snapshot endpoint for recent forwarder log summaries. Use `/api/v1/admin/onboarding/forwarder-logs/page` for operator tables.",
        deprecated = true
    )
    public List<ForwarderLogRecord> listForwarderLogs(
        @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        return adminService.listForwarderLogs(limit);
    }
}
