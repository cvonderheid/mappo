package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.ProviderConnectionCreateRequest;
import com.mappo.controlplane.api.request.ProviderConnectionPatchRequest;
import com.mappo.controlplane.model.ProviderConnectionRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCommandService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/provider-connections")
@RequiredArgsConstructor
@Tag(name = "Provider Connections", description = "Provider API auth/scope configuration used by project deployment drivers.")
public class ProviderConnectionsController {

    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionCommandService providerConnectionCommandService;

    @GetMapping
    @Operation(summary = "List provider connections", description = "Returns configured provider connections and linked projects.")
    public List<ProviderConnectionRecord> listConnections() {
        return providerConnectionCatalogService.listConnections();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create provider connection")
    public ProviderConnectionRecord createConnection(
        @Valid @RequestBody ProviderConnectionCreateRequest request
    ) {
        return providerConnectionCommandService.createConnection(request);
    }

    @PatchMapping("/{connectionId}")
    @Operation(summary = "Patch provider connection")
    public ProviderConnectionRecord patchConnection(
        @PathVariable("connectionId") String connectionId,
        @RequestBody(required = false) ProviderConnectionPatchRequest patchRequest
    ) {
        return providerConnectionCommandService.patchConnection(connectionId, patchRequest);
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete provider connection")
    public void deleteConnection(@PathVariable("connectionId") String connectionId) {
        providerConnectionCommandService.deleteConnection(connectionId);
    }
}
