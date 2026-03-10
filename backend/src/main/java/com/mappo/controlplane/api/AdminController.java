package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.ForwarderLogIngestRequest;
import com.mappo.controlplane.api.request.OnboardingEventRequest;
import com.mappo.controlplane.api.request.ReleaseManifestIngestRequest;
import com.mappo.controlplane.api.request.TargetRegistrationPatchRequest;
import com.mappo.controlplane.api.query.ForwarderLogPageParameters;
import com.mappo.controlplane.api.query.MarketplaceEventPageParameters;
import com.mappo.controlplane.api.query.ReleaseWebhookDeliveryPageParameters;
import com.mappo.controlplane.api.query.TargetRegistrationPageParameters;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.DeleteRegistrationResultRecord;
import com.mappo.controlplane.model.EventIngestResultRecord;
import com.mappo.controlplane.model.ForwarderLogPageRecord;
import com.mappo.controlplane.model.ForwarderLogIngestResultRecord;
import com.mappo.controlplane.model.MarketplaceEventPageRecord;
import com.mappo.controlplane.model.ReleaseWebhookDeliveryPageRecord;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.TargetRegistrationPageRecord;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.service.AdminService;
import com.mappo.controlplane.service.release.ReleaseManifestIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
@Tag(name = "Admin", description = "Onboarding, registration, webhook, and operational audit endpoints.")
public class AdminController {

    private final AdminService adminService;
    private final ReleaseManifestIngestService releaseManifestIngestService;
    private final MappoProperties properties;

    @PostMapping("/onboarding/events")
    @Operation(summary = "Ingest marketplace onboarding event")
    public EventIngestResultRecord ingestMarketplaceEvent(
        @Valid @RequestBody OnboardingEventRequest request,
        @RequestHeader(value = "x-mappo-ingest-token", required = false) String ingestToken
    ) {
        validateIngestToken(ingestToken);
        return adminService.ingestMarketplaceEvent(request);
    }

    @GetMapping("/onboarding/events")
    @Operation(summary = "List onboarding events", description = "Primary paginated admin endpoint for onboarding-event history.")
    public MarketplaceEventPageRecord listMarketplaceEvents(
        @Valid @ParameterObject @ModelAttribute MarketplaceEventPageParameters parameters
    ) {
        return adminService.listMarketplaceEventsPage(parameters.toQuery());
    }

    @PostMapping("/onboarding/forwarder-logs")
    @Operation(summary = "Ingest marketplace forwarder log")
    public ForwarderLogIngestResultRecord ingestForwarderLog(
        @Valid @RequestBody ForwarderLogIngestRequest request,
        @RequestHeader(value = "x-mappo-ingest-token", required = false) String ingestToken
    ) {
        validateIngestToken(ingestToken);
        return adminService.ingestForwarderLog(request);
    }

    @GetMapping("/onboarding/forwarder-logs/page")
    @Operation(summary = "List forwarder logs", description = "Primary paginated admin endpoint for marketplace forwarder logs.")
    public ForwarderLogPageRecord listForwarderLogsPage(
        @Valid @ParameterObject @ModelAttribute ForwarderLogPageParameters parameters
    ) {
        return adminService.listForwarderLogsPage(parameters.toQuery());
    }

    @GetMapping("/onboarding/registrations")
    @Operation(summary = "List registered targets", description = "Primary paginated admin endpoint for registered target metadata.")
    public TargetRegistrationPageRecord listRegistrations(
        @Valid @ParameterObject @ModelAttribute TargetRegistrationPageParameters parameters
    ) {
        return adminService.listRegistrationsPage(parameters.toQuery());
    }

    @PostMapping("/releases/ingest/github")
    @Operation(summary = "Ingest releases from GitHub manifest")
    public ReleaseManifestIngestResultRecord ingestGithubReleaseManifest(
        @RequestBody(required = false) ReleaseManifestIngestRequest request
    ) {
        return releaseManifestIngestService.ingestGithubManifest(request);
    }

    @PostMapping("/releases/webhooks/github")
    @Operation(summary = "Receive GitHub release-manifest webhook")
    public ReleaseManifestIngestResultRecord ingestGithubReleaseWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "x-github-event", required = false) String githubEvent,
        @RequestHeader(value = "x-hub-signature-256", required = false) String signatureHeader,
        @RequestHeader(value = "x-github-delivery", required = false) String githubDeliveryId
    ) {
        return releaseManifestIngestService.ingestGithubWebhook(payload, githubEvent, signatureHeader, githubDeliveryId);
    }

    @PatchMapping("/onboarding/registrations/{targetId}")
    @Operation(summary = "Update registered target metadata")
    public TargetRegistrationRecord updateRegistration(
        @PathVariable("targetId") String targetId,
        @RequestBody TargetRegistrationPatchRequest patch
    ) {
        return adminService.updateTargetRegistration(targetId, patch);
    }

    @DeleteMapping("/onboarding/registrations/{targetId}")
    @Operation(summary = "Delete a registered target")
    public DeleteRegistrationResultRecord deleteRegistration(@PathVariable("targetId") String targetId) {
        adminService.deleteTargetRegistration(targetId);
        return new DeleteRegistrationResultRecord(targetId, true);
    }

    @GetMapping("/releases/webhook-deliveries")
    @Operation(summary = "List release webhook deliveries", description = "Primary paginated admin endpoint for GitHub release-webhook audit records.")
    public ReleaseWebhookDeliveryPageRecord listReleaseWebhookDeliveries(
        @Valid @ParameterObject @ModelAttribute ReleaseWebhookDeliveryPageParameters parameters
    ) {
        return adminService.listReleaseWebhookDeliveriesPage(parameters.toQuery());
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
