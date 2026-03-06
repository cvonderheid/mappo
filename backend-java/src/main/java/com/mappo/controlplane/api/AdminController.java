package com.mappo.controlplane.api;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.service.AdminService;
import java.util.List;
import java.util.Map;
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
public class AdminController {

    private final AdminService adminService;
    private final MappoProperties properties;

    public AdminController(AdminService adminService, MappoProperties properties) {
        this.adminService = adminService;
        this.properties = properties;
    }

    @GetMapping("/onboarding")
    public Map<String, Object> onboardingSnapshot(
        @RequestParam(value = "event_limit", defaultValue = "50") int eventLimit
    ) {
        return adminService.getOnboardingSnapshot(eventLimit);
    }

    @PostMapping("/onboarding/events")
    public Map<String, Object> ingestMarketplaceEvent(
        @RequestBody Map<String, Object> request,
        @RequestHeader(value = "x-mappo-ingest-token", required = false) String ingestToken
    ) {
        validateIngestToken(ingestToken);
        return adminService.ingestMarketplaceEvent(request);
    }

    @GetMapping("/onboarding/forwarder-logs")
    public List<Map<String, Object>> listForwarderLogs(
        @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        return adminService.listForwarderLogs(limit);
    }

    @PostMapping("/onboarding/forwarder-logs")
    public Map<String, Object> ingestForwarderLog(
        @RequestBody Map<String, Object> request,
        @RequestHeader(value = "x-mappo-ingest-token", required = false) String ingestToken
    ) {
        validateIngestToken(ingestToken);
        return adminService.ingestForwarderLog(request);
    }

    @PatchMapping("/onboarding/registrations/{targetId}")
    public Map<String, Object> updateRegistration(
        @PathVariable("targetId") String targetId,
        @RequestBody Map<String, Object> patch
    ) {
        return adminService.updateTargetRegistration(targetId, patch);
    }

    @DeleteMapping("/onboarding/registrations/{targetId}")
    public Map<String, Object> deleteRegistration(@PathVariable("targetId") String targetId) {
        adminService.deleteTargetRegistration(targetId);
        return Map.of("target_id", targetId, "deleted", true);
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
