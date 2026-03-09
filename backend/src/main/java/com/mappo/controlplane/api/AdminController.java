package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.ForwarderLogIngestRequest;
import com.mappo.controlplane.api.request.OnboardingEventRequest;
import com.mappo.controlplane.api.request.ReleaseManifestIngestRequest;
import com.mappo.controlplane.api.request.TargetRegistrationPatchRequest;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.DeleteRegistrationResultRecord;
import com.mappo.controlplane.model.EventIngestResultRecord;
import com.mappo.controlplane.model.ForwarderLogPageRecord;
import com.mappo.controlplane.model.ForwarderLogIngestResultRecord;
import com.mappo.controlplane.model.ForwarderLogRecord;
import com.mappo.controlplane.model.MarketplaceEventPageRecord;
import com.mappo.controlplane.model.OnboardingSnapshotRecord;
import com.mappo.controlplane.model.ReleaseWebhookDeliveryPageRecord;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.TargetRegistrationPageRecord;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.model.query.ForwarderLogPageQuery;
import com.mappo.controlplane.model.query.MarketplaceEventPageQuery;
import com.mappo.controlplane.model.query.ReleaseWebhookDeliveryPageQuery;
import com.mappo.controlplane.model.query.TargetRegistrationPageQuery;
import com.mappo.controlplane.service.AdminService;
import com.mappo.controlplane.service.release.ReleaseManifestIngestService;
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
    private final ReleaseManifestIngestService releaseManifestIngestService;
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

    @GetMapping("/onboarding/events")
    public MarketplaceEventPageRecord listMarketplaceEvents(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "25") int size,
        @RequestParam(value = "eventId", required = false) String eventId,
        @RequestParam(value = "status", required = false) String status
    ) {
        return adminService.listMarketplaceEventsPage(new MarketplaceEventPageQuery(page, size, eventId, status));
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

    @GetMapping("/onboarding/forwarder-logs/page")
    public ForwarderLogPageRecord listForwarderLogsPage(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "25") int size,
        @RequestParam(value = "logId", required = false) String logId,
        @RequestParam(value = "level", required = false) String level
    ) {
        return adminService.listForwarderLogsPage(new ForwarderLogPageQuery(page, size, logId, level));
    }

    @GetMapping("/onboarding/registrations")
    public TargetRegistrationPageRecord listRegistrations(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "25") int size,
        @RequestParam(value = "targetId", required = false) String targetId,
        @RequestParam(value = "ring", required = false) String ring,
        @RequestParam(value = "region", required = false) String region,
        @RequestParam(value = "tier", required = false) String tier
    ) {
        return adminService.listRegistrationsPage(
            new TargetRegistrationPageQuery(page, size, targetId, ring, region, tier)
        );
    }

    @PostMapping("/releases/ingest/github")
    public ReleaseManifestIngestResultRecord ingestGithubReleaseManifest(
        @RequestBody(required = false) ReleaseManifestIngestRequest request
    ) {
        return releaseManifestIngestService.ingestGithubManifest(request);
    }

    @PostMapping("/releases/webhooks/github")
    public ReleaseManifestIngestResultRecord ingestGithubReleaseWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "x-github-event", required = false) String githubEvent,
        @RequestHeader(value = "x-hub-signature-256", required = false) String signatureHeader,
        @RequestHeader(value = "x-github-delivery", required = false) String githubDeliveryId
    ) {
        return releaseManifestIngestService.ingestGithubWebhook(payload, githubEvent, signatureHeader, githubDeliveryId);
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

    @GetMapping("/releases/webhook-deliveries")
    public ReleaseWebhookDeliveryPageRecord listReleaseWebhookDeliveries(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "25") int size,
        @RequestParam(value = "deliveryId", required = false) String deliveryId,
        @RequestParam(value = "status", required = false) String status
    ) {
        return adminService.listReleaseWebhookDeliveriesPage(
            new ReleaseWebhookDeliveryPageQuery(page, size, deliveryId, status)
        );
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
