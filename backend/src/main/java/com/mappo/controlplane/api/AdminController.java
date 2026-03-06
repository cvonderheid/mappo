package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.ForwarderLogIngestRequest;
import com.mappo.controlplane.api.request.OnboardingEventRequest;
import com.mappo.controlplane.api.request.TargetRegistrationPatchRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.DeleteRegistrationResultRecord;
import com.mappo.controlplane.model.EventIngestResultRecord;
import com.mappo.controlplane.model.ForwarderLogIngestResultRecord;
import com.mappo.controlplane.model.ForwarderLogRecord;
import com.mappo.controlplane.model.OnboardingSnapshotRecord;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final MappoProperties properties;

    @GetMapping("/onboarding")
    public OnboardingSnapshotRecord onboardingSnapshot(
        @RequestParam(value = "event_limit", defaultValue = "50") int eventLimit
    ) {
        return adminService.getOnboardingSnapshot(eventLimit);
    }

    @PostMapping("/onboarding/events")
    public EventIngestResultRecord ingestMarketplaceEvent(
        @Valid @RequestBody OnboardingEventRequest request,
        @RequestHeader(value = "x-mappo-ingest-token", required = false) String ingestToken
    ) {
        validateIngestToken(ingestToken);
        return adminService.ingestMarketplaceEvent(request);
    }

    @GetMapping("/onboarding/forwarder-logs")
    public List<ForwarderLogRecord> listForwarderLogs(
        @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        return adminService.listForwarderLogs(limit);
    }

    @PostMapping("/onboarding/forwarder-logs")
    public ForwarderLogIngestResultRecord ingestForwarderLog(
        @Valid @RequestBody ForwarderLogIngestRequest request,
        @RequestHeader(value = "x-mappo-ingest-token", required = false) String ingestToken
    ) {
        validateIngestToken(ingestToken);
        return adminService.ingestForwarderLog(request);
    }

    @PatchMapping("/onboarding/registrations/{targetId}")
    public TargetRegistrationRecord updateRegistration(
        @PathVariable("targetId") String targetId,
        @RequestBody TargetRegistrationPatchRequest patch
    ) {
        return adminService.updateTargetRegistration(targetId, patch);
    }

    @DeleteMapping("/onboarding/registrations/{targetId}")
    public DeleteRegistrationResultRecord deleteRegistration(@PathVariable("targetId") String targetId) {
        adminService.deleteTargetRegistration(targetId);
        return new DeleteRegistrationResultRecord(targetId, true);
    }

    private void validateIngestToken(String ingestToken) {
        String requiredToken = properties.getMarketplaceIngestToken();
        if (requiredToken != null && !requiredToken.isBlank()) {
            if (ingestToken == null || !requiredToken.equals(ingestToken)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid marketplace ingest token");
            }
        }
    }
}
