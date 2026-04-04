package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.ProviderConnectionCreateRequest;
import com.mappo.controlplane.api.request.ProviderConnectionPatchRequest;
import com.mappo.controlplane.api.request.ProviderConnectionVerifyRequest;
import com.mappo.controlplane.model.ProviderConnectionAdoProjectDiscoveryResultRecord;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCommandService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionDiscoveryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/provider-connections")
@RequiredArgsConstructor
@Tag(name = "Deployment Connections", description = "Outbound authenticated connection records MAPPO uses when deployment drivers call external systems.")
public class ProviderConnectionsController {

    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionCommandService providerConnectionCommandService;
    private final ProviderConnectionDiscoveryService providerConnectionDiscoveryService;

    @GetMapping
    @Operation(summary = "List deployment connections", description = "Returns configured deployment connections and linked projects.")
    public List<ProviderConnectionRecord> listConnections() {
        return providerConnectionCatalogService.listConnections();
    }

    @GetMapping("/{connectionId}/ado/projects/discover")
    @Operation(
        summary = "Discover Azure DevOps projects",
        description = "Lists Azure DevOps projects reachable through the selected deployment connection using its configured PAT and verified Azure DevOps account URL."
    )
    public ProviderConnectionAdoProjectDiscoveryResultRecord discoverAdoProjects(
        @PathVariable("connectionId") String connectionId,
        @RequestParam(name = "nameContains", required = false) String nameContains
    ) {
        return providerConnectionDiscoveryService.discoverAdoProjects(connectionId, nameContains);
    }

    @PostMapping("/ado/verify")
    @Operation(
        summary = "Verify Azure DevOps deployment connection draft",
        description = "Normalizes the submitted Azure DevOps account URL, resolves the configured PAT source, and enumerates reachable Azure DevOps projects without persisting the deployment connection."
    )
    public ProviderConnectionAdoProjectDiscoveryResultRecord verifyAdoConnection(
        @RequestBody(required = false) ProviderConnectionVerifyRequest request,
        @RequestParam(name = "nameContains", required = false) String nameContains
    ) {
        return providerConnectionDiscoveryService.verifyAdoConnection(request, nameContains);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create deployment connection")
    public ProviderConnectionRecord createConnection(
        @Valid @RequestBody ProviderConnectionCreateRequest request
    ) {
        return providerConnectionCommandService.createConnection(request);
    }

    @PatchMapping("/{connectionId}")
    @Operation(summary = "Patch deployment connection")
    public ProviderConnectionRecord patchConnection(
        @PathVariable("connectionId") String connectionId,
        @RequestBody(required = false) ProviderConnectionPatchRequest patchRequest
    ) {
        return providerConnectionCommandService.patchConnection(connectionId, patchRequest);
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete deployment connection")
    public void deleteConnection(@PathVariable("connectionId") String connectionId) {
        providerConnectionCommandService.deleteConnection(connectionId);
    }
}
