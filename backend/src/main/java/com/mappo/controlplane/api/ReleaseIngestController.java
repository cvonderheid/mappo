package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.ReleaseIngestEndpointCreateRequest;
import com.mappo.controlplane.api.request.ReleaseIngestEndpointPatchRequest;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointCatalogService;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointCommandService;
import com.mappo.controlplane.service.release.ReleaseManifestIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/release-ingest/endpoints")
@RequiredArgsConstructor
@Tag(name = "Release Ingest", description = "Release-ingest endpoint configuration and project link visibility.")
public class ReleaseIngestController {

    private final ReleaseIngestEndpointCatalogService releaseIngestEndpointCatalogService;
    private final ReleaseIngestEndpointCommandService releaseIngestEndpointCommandService;
    private final ReleaseManifestIngestService releaseManifestIngestService;

    @GetMapping
    @Operation(summary = "List release ingest endpoints", description = "Returns all configured release ingest endpoints and linked projects.")
    public List<ReleaseIngestEndpointRecord> listEndpoints() {
        return releaseIngestEndpointCatalogService.listEndpoints();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create release ingest endpoint")
    public ReleaseIngestEndpointRecord createEndpoint(
        @Valid @RequestBody ReleaseIngestEndpointCreateRequest request
    ) {
        return releaseIngestEndpointCommandService.createEndpoint(request);
    }

    @PatchMapping("/{endpointId}")
    @Operation(summary = "Patch release ingest endpoint")
    public ReleaseIngestEndpointRecord patchEndpoint(
        @PathVariable("endpointId") String endpointId,
        @RequestBody(required = false) ReleaseIngestEndpointPatchRequest patchRequest
    ) {
        return releaseIngestEndpointCommandService.patchEndpoint(endpointId, patchRequest);
    }

    @DeleteMapping("/{endpointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete release ingest endpoint")
    public void deleteEndpoint(@PathVariable("endpointId") String endpointId) {
        releaseIngestEndpointCommandService.deleteEndpoint(endpointId);
    }

    @PostMapping("/{endpointId}/webhooks/github")
    @Operation(summary = "Receive GitHub release-manifest webhook for endpoint")
    public ReleaseManifestIngestResultRecord ingestGithubWebhook(
        @PathVariable("endpointId") String endpointId,
        @RequestBody(required = false) String payload,
        @RequestHeader(value = "x-github-event", required = false) String githubEvent,
        @RequestHeader(value = "x-hub-signature-256", required = false) String signatureHeader,
        @RequestHeader(value = "x-github-delivery", required = false) String githubDeliveryId
    ) {
        return releaseManifestIngestService.ingestGithubWebhookForEndpoint(
            endpointId,
            payload,
            githubEvent,
            signatureHeader,
            githubDeliveryId
        );
    }

    @PostMapping("/{endpointId}/webhooks/ado")
    @Operation(summary = "Receive Azure DevOps release webhook for endpoint")
    public ReleaseManifestIngestResultRecord ingestAzureDevOpsWebhook(
        @PathVariable("endpointId") String endpointId,
        @RequestBody(required = false) String payload,
        @RequestHeader(value = "x-event-type", required = false) String eventType,
        @RequestHeader(value = "x-vss-deliveryid", required = false) String deliveryId,
        @RequestHeader(value = "authorization", required = false) String authorizationHeader,
        @RequestParam(value = "token", required = false) String queryToken,
        @RequestParam(value = "projectId", required = false) String projectId
    ) {
        return releaseManifestIngestService.ingestAzureDevOpsWebhookForEndpoint(
            endpointId,
            payload,
            eventType,
            deliveryId,
            authorizationHeader,
            queryToken,
            projectId
        );
    }
}
